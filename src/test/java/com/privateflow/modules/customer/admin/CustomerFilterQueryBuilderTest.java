package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerFilterQueryBuilderTest {

  private final CustomerFilterQueryBuilder builder = new CustomerFilterQueryBuilder();

  @Test
  void combinesKeywordAndRestrictedKeeperScopeWithStableArguments() {
    CustomerFilter filter = new CustomerFilter(
        "Alice-138", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        null, null, List.of(), TagGroupLogic.AND, CustomerSortField.UPDATED_AT,
        SortDirection.DESC, 1, 20);
    CustomerAccessScope scope = new CustomerAccessScope(
        false, List.of("keeper-1", "keeper-2"), true);

    CustomerQuerySpec spec = builder.build(filter, scope);

    assertThat(spec.whereClause())
        .contains("INSTR(COALESCE(c.nickname, ''), ?) > 0")
        .contains("INSTR(c.phone, ?) > 0")
        .contains("c.assigned_keeper IN (?, ?)");
    assertThat(spec.args()).containsExactly(
        "Alice-138", "Alice-138", "Alice-138", "Alice-138", "Alice-138", "Alice-138", "Alice-138",
        "138", "keeper-1", "keeper-2");
    assertThat(spec.orderClause()).isEqualTo("c.updated_at DESC, c.id DESC");
  }

  @Test
  void combinesStructuredFieldsAndAnyAllTagGroups() {
    LocalDateTime from = LocalDateTime.of(2026, 7, 1, 0, 0);
    LocalDateTime to = LocalDateTime.of(2026, 7, 16, 23, 59);
    CustomerFilter filter = new CustomerFilter(
        "",
        List.of("企微", "抖音"),
        List.of("XIAN_SUO"),
        List.of(),
        List.of("万江店"),
        List.of(),
        List.of("待联系"),
        from,
        to,
        List.of(
            new TagFilterGroup(7L, List.of(101L, 102L), TagMatchMode.ANY),
            new TagFilterGroup(8L, List.of(201L, 202L), TagMatchMode.ALL)),
        TagGroupLogic.AND,
        CustomerSortField.NICKNAME,
        SortDirection.ASC,
        1,
        20);

    CustomerQuerySpec spec = builder.build(filter, CustomerAccessScope.all());

    assertThat(spec.whereClause())
        .contains("c.source_channel IN (?, ?)")
        .contains("c.lead_type IN (?)")
        .contains("c.intended_store IN (?)")
        .contains("c.customer_stage IN (?)")
        .contains("c.updated_at >= ?")
        .contains("c.updated_at <= ?")
        .contains("JOIN tag_categories tc")
        .contains("JOIN tag_values tv")
        .contains("a.is_active = 1")
        .contains("a.category_id = ? AND a.tag_value_id IN (?, ?)")
        .contains("COUNT(DISTINCT a.tag_value_id)")
        .contains(") = ?");
    assertThat(spec.args()).containsSubsequence(
        "企微", "抖音", "XIAN_SUO", "万江店", "待联系", from, to,
        7L, 101L, 102L, 8L, 201L, 202L, 2);
    assertThat(spec.orderClause()).isEqualTo("c.nickname ASC, c.id ASC");
  }
}
