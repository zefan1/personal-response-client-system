package com.privateflow.modules.customer.admin;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CustomerFilterQueryBuilder {

  public CustomerQuerySpec build(CustomerFilter filter, CustomerAccessScope accessScope) {
    CustomerFilter safeFilter = filter == null ? CustomerFilter.empty() : filter;
    CustomerAccessScope safeScope = accessScope == null ? CustomerAccessScope.all() : accessScope;
    StringBuilder where = new StringBuilder(" WHERE 1=1");
    List<Object> args = new ArrayList<>();

    appendKeyword(where, args, safeFilter.keyword());
    appendIn(where, args, "c.source_channel", safeFilter.sourceChannels());
    appendIn(where, args, "c.lead_type", safeFilter.leadTypes());
    appendIn(where, args, "c.assigned_keeper", safeFilter.assignedKeepers());
    appendIn(where, args, "c.intended_store", safeFilter.intendedStores());
    appendIn(where, args, "c.intended_project", safeFilter.intendedProjects());
    appendIn(where, args, "c.customer_stage", safeFilter.customerStages());
    if (safeFilter.updatedFrom() != null) {
      where.append(" AND c.updated_at >= ?");
      args.add(safeFilter.updatedFrom());
    }
    if (safeFilter.updatedTo() != null) {
      where.append(" AND c.updated_at <= ?");
      args.add(safeFilter.updatedTo());
    }
    appendTagGroups(where, args, safeFilter);
    appendAccessScope(where, args, safeScope);

    return new CustomerQuerySpec(where.toString(), args, orderClause(safeFilter));
  }

  private void appendKeyword(StringBuilder where, List<Object> args, String keyword) {
    String text = keyword == null ? "" : keyword.trim();
    if (text.isBlank()) {
      return;
    }
    where.append("""
         AND (
              INSTR(COALESCE(c.nickname, ''), ?) > 0
           OR INSTR(COALESCE(c.source_channel, ''), ?) > 0
           OR INSTR(COALESCE(c.intended_store, ''), ?) > 0
           OR INSTR(COALESCE(c.intended_project, ''), ?) > 0
           OR INSTR(COALESCE(c.assigned_keeper, ''), ?) > 0
           OR INSTR(COALESCE(c.customer_stage, ''), ?) > 0
           OR INSTR(COALESCE(c.source_row_id, ''), ?) > 0
        """);
    for (int i = 0; i < 7; i++) {
      args.add(text);
    }
    String digits = text.replaceAll("[^\\d]", "");
    if (!digits.isBlank()) {
      where.append(" OR INSTR(c.phone, ?) > 0");
      args.add(digits);
    }
    where.append(")");
  }

  private void appendAccessScope(
      StringBuilder where,
      List<Object> args,
      CustomerAccessScope accessScope) {
    if (accessScope.unrestricted()) {
      return;
    }
    if (accessScope.permittedKeepers().isEmpty()) {
      where.append(" AND 1=0");
      return;
    }
    where.append(" AND c.assigned_keeper IN (")
        .append(placeholders(accessScope.permittedKeepers().size()))
        .append(")");
    args.addAll(accessScope.permittedKeepers());
  }

  private void appendIn(
      StringBuilder where,
      List<Object> args,
      String column,
      List<?> values) {
    if (values == null || values.isEmpty()) {
      return;
    }
    where.append(" AND ")
        .append(column)
        .append(" IN (")
        .append(placeholders(values.size()))
        .append(")");
    args.addAll(values);
  }

  private void appendTagGroups(
      StringBuilder where,
      List<Object> args,
      CustomerFilter filter) {
    if (filter.tagGroups() == null || filter.tagGroups().isEmpty()) {
      return;
    }
    List<String> predicates = new ArrayList<>();
    for (TagFilterGroup group : filter.tagGroups()) {
      predicates.add(tagPredicate(group, args));
    }
    String connector = filter.tagGroupLogic() == TagGroupLogic.OR ? " OR " : " AND ";
    where.append(" AND (").append(String.join(connector, predicates)).append(")");
  }

  private String tagPredicate(TagFilterGroup group, List<Object> args) {
    args.add(group.categoryId());
    args.addAll(group.valueIds());
    String values = placeholders(group.valueIds().size());
    if (group.match() != TagMatchMode.ALL) {
      return """
          EXISTS (
            SELECT 1
            FROM customer_tag_assignments a
            JOIN tag_categories tc ON tc.id = a.category_id
              AND tc.is_enabled = 1 AND tc.merged_into_id IS NULL AND tc.use_for_filter = 1
            JOIN tag_values tv ON tv.id = a.tag_value_id AND tv.category_id = a.category_id
              AND tv.is_enabled = 1 AND tv.merged_into_id IS NULL
            WHERE a.customer_id = c.id AND a.is_active = 1
              AND a.category_id = ? AND a.tag_value_id IN (%s)
          )
          """.formatted(values).trim();
    }
    args.add(group.valueIds().size());
    return """
        (SELECT COUNT(DISTINCT a.tag_value_id)
         FROM customer_tag_assignments a
         JOIN tag_categories tc ON tc.id = a.category_id
           AND tc.is_enabled = 1 AND tc.merged_into_id IS NULL AND tc.use_for_filter = 1
         JOIN tag_values tv ON tv.id = a.tag_value_id AND tv.category_id = a.category_id
           AND tv.is_enabled = 1 AND tv.merged_into_id IS NULL
         WHERE a.customer_id = c.id AND a.is_active = 1
           AND a.category_id = ? AND a.tag_value_id IN (%s)
        ) = ?
        """.formatted(values).trim();
  }

  private String orderClause(CustomerFilter filter) {
    String column = switch (filter.sortBy() == null ? CustomerSortField.UPDATED_AT : filter.sortBy()) {
      case CREATED_AT -> "c.created_at";
      case NICKNAME -> "c.nickname";
      case ID -> "c.id";
      case UPDATED_AT -> "c.updated_at";
    };
    String direction = filter.sortDirection() == SortDirection.ASC ? " ASC" : " DESC";
    if (filter.sortBy() == CustomerSortField.ID) {
      return column + direction;
    }
    return column + direction + ", c.id" + direction;
  }

  private String placeholders(int count) {
    return String.join(", ", java.util.Collections.nCopies(count, "?"));
  }
}
