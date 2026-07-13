package com.privateflow.modules.customer.admin;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRowMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerAdminSearchRepository {

  private static final CustomerRowMapper ROW_MAPPER = new CustomerRowMapper();
  private final JdbcTemplate jdbcTemplate;

  public CustomerAdminSearchRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public CustomerAdminSearchPage search(String keyword, int page, int size) {
    QuerySpec query = querySpec(keyword);
    Long totalValue = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM customers" + query.whereClause(),
        Long.class,
        query.args().toArray());
    long total = totalValue == null ? 0L : totalValue;
    int totalPages = Math.max(1, (int) Math.ceil(total / (double) size));
    int offset = (page - 1) * size;

    List<Object> pageArgs = new ArrayList<>(query.args());
    pageArgs.add(size);
    pageArgs.add(offset);
    List<CustomerAdminListItem> items = jdbcTemplate.query(
        "SELECT * FROM customers" + query.whereClause() + " ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?",
        ROW_MAPPER,
        pageArgs.toArray()).stream().map(this::toListItem).toList();
    return new CustomerAdminSearchPage(items, total, page, size, totalPages);
  }

  private QuerySpec querySpec(String keyword) {
    String text = keyword == null ? "" : keyword.trim();
    if (text.isBlank()) {
      return new QuerySpec("", List.of());
    }
    String digits = text.replaceAll("[^\\d]", "");
    List<Object> args = new ArrayList<>();
    StringBuilder where = new StringBuilder("""
         WHERE (
              INSTR(COALESCE(nickname, ''), ?) > 0
           OR INSTR(COALESCE(source_channel, ''), ?) > 0
           OR INSTR(COALESCE(intended_store, ''), ?) > 0
           OR INSTR(COALESCE(intended_project, ''), ?) > 0
           OR INSTR(COALESCE(assigned_keeper, ''), ?) > 0
           OR INSTR(COALESCE(customer_stage, ''), ?) > 0
           OR INSTR(COALESCE(source_row_id, ''), ?) > 0
        """);
    for (int i = 0; i < 7; i++) {
      args.add(text);
    }
    if (!digits.isBlank()) {
      where.append(" OR INSTR(phone, ?) > 0 ");
      args.add(digits);
    }
    where.append(")");
    return new QuerySpec(where.toString(), args);
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

  private record QuerySpec(String whereClause, List<Object> args) {
  }
}
