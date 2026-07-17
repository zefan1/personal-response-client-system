package com.privateflow.modules.customer.admin;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRowMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerAdminSearchRepository {

  private static final CustomerRowMapper ROW_MAPPER = new CustomerRowMapper();
  private final JdbcTemplate jdbcTemplate;
  private final CustomerFilterQueryBuilder queryBuilder;

  public CustomerAdminSearchRepository(JdbcTemplate jdbcTemplate) {
    this(jdbcTemplate, new CustomerFilterQueryBuilder());
  }

  public CustomerAdminSearchRepository(
      JdbcTemplate jdbcTemplate,
      CustomerFilterQueryBuilder queryBuilder) {
    this.jdbcTemplate = jdbcTemplate;
    this.queryBuilder = queryBuilder;
  }

  public CustomerAdminSearchPage search(CustomerFilter filter, CustomerAccessScope accessScope) {
    CustomerFilter safeFilter = filter == null ? CustomerFilter.empty() : filter;
    CustomerQuerySpec query = queryBuilder.build(safeFilter, accessScope);
    long total = count(query);
    int totalPages = Math.max(1, (int) Math.ceil(total / (double) safeFilter.pageSize()));
    int offset = (safeFilter.page() - 1) * safeFilter.pageSize();
    List<CustomerAdminListItem> enrichedItems = loadRows(query, safeFilter.pageSize(), offset);
    return new CustomerAdminSearchPage(
        enrichedItems, total, safeFilter.page(), safeFilter.pageSize(), totalPages);
  }

  public long count(CustomerFilter filter, CustomerAccessScope accessScope) {
    CustomerFilter safeFilter = filter == null ? CustomerFilter.empty() : filter;
    return count(queryBuilder.build(safeFilter, accessScope));
  }

  public List<CustomerAdminListItem> exportRows(
      CustomerFilter filter,
      CustomerAccessScope accessScope,
      int limit) {
    if (limit <= 0) {
      return List.of();
    }
    CustomerFilter safeFilter = filter == null ? CustomerFilter.empty() : filter;
    return loadRows(queryBuilder.build(safeFilter, accessScope), limit, 0);
  }

  public CustomerAdminSearchPage search(String keyword, int page, int size) {
    return search(new CustomerFilter(
        keyword, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        null, null, List.of(), TagGroupLogic.AND, CustomerSortField.UPDATED_AT,
        SortDirection.DESC, page, size), CustomerAccessScope.all());
  }

  private long count(CustomerQuerySpec query) {
    Long totalValue = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM customers c" + query.whereClause(),
        Long.class,
        query.args().toArray());
    return totalValue == null ? 0L : totalValue;
  }

  private List<CustomerAdminListItem> loadRows(CustomerQuerySpec query, int limit, int offset) {
    List<Object> pageArgs = new ArrayList<>(query.args());
    pageArgs.add(limit);
    pageArgs.add(Math.max(0, offset));
    List<CustomerAdminListItem> items = jdbcTemplate.query(
        "SELECT c.* FROM customers c" + query.whereClause()
            + " ORDER BY " + query.orderClause() + " LIMIT ? OFFSET ?",
        ROW_MAPPER,
        pageArgs.toArray()).stream().map(this::toListItem).toList();
    Map<Long, List<CustomerTagSummary>> tagsByCustomer = loadCurrentTagSummaries(
        items.stream().map(CustomerAdminListItem::id).toList());
    return items.stream()
        .map(item -> item.withTags(tagsByCustomer.getOrDefault(item.id(), List.of())))
        .toList();
  }

  private CustomerAdminListItem toListItem(Customer customer) {
    return new CustomerAdminListItem(
        customer.getId(),
        customer.getPhone(),
        customer.getNickname(),
        customer.getSourceChannel(),
        customer.getLeadType(),
        customer.getAssignedKeeper(),
        customer.getIntendedStore(),
        customer.getIntendedProject(),
        customer.getCustomerStage(),
        customer.getIntentLevel(),
        customer.getLastFollowupAt(),
        customer.getNextFollowupAt(),
        customer.getAppointmentDate(),
        customer.getAppointmentStore(),
        customer.getAppointmentItem(),
        customer.getArrived(),
        customer.getSourceTable(),
        customer.getUpdatedAt());
  }

  private Map<Long, List<CustomerTagSummary>> loadCurrentTagSummaries(List<Long> customerIds) {
    if (customerIds == null || customerIds.isEmpty()) {
      return Map.of();
    }
    String placeholders = String.join(", ", java.util.Collections.nCopies(customerIds.size(), "?"));
    List<Map.Entry<Long, CustomerTagSummary>> rows = jdbcTemplate.query("""
        SELECT a.customer_id, tc.id AS category_id, tc.category_key, tc.category_name,
               tv.id AS value_id, tv.tag_value, tv.display_name
        FROM customer_tag_assignments a
        JOIN tag_categories tc ON tc.id = a.category_id
          AND tc.is_enabled = 1 AND tc.merged_into_id IS NULL
        JOIN tag_values tv ON tv.id = a.tag_value_id AND tv.category_id = a.category_id
          AND tv.is_enabled = 1 AND tv.merged_into_id IS NULL
        WHERE a.is_active = 1 AND a.customer_id IN (%s)
        ORDER BY a.customer_id, tc.sort_order, tc.id, tv.sort_order, tv.id
        """.formatted(placeholders), (rs, rowNum) -> Map.entry(
        rs.getLong("customer_id"),
        new CustomerTagSummary(
            rs.getLong("category_id"),
            rs.getString("category_key"),
            rs.getString("category_name"),
            rs.getLong("value_id"),
            rs.getString("tag_value"),
            rs.getString("display_name"))), customerIds.toArray());
    return rows.stream().collect(Collectors.groupingBy(
        Map.Entry::getKey,
        LinkedHashMap::new,
        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
  }
}
