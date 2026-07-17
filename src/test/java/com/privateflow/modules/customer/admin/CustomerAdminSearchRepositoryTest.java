package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CustomerAdminSearchRepositoryTest {

  private JdbcTemplate jdbcTemplate;
  private CustomerAdminSearchRepository repository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:customer_admin_search;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS customer_tag_assignments");
    jdbcTemplate.execute("DROP TABLE IF EXISTS tag_values");
    jdbcTemplate.execute("DROP TABLE IF EXISTS tag_categories");
    jdbcTemplate.execute("DROP TABLE IF EXISTS customers");
    jdbcTemplate.execute("""
        CREATE TABLE customers (
          id BIGINT PRIMARY KEY,
          phone VARCHAR(20) NOT NULL,
          nickname VARCHAR(100),
          source_channel VARCHAR(50),
          lead_type VARCHAR(20),
          personality_type VARCHAR(50),
          assigned_keeper VARCHAR(50),
          intended_store VARCHAR(100),
          intended_project VARCHAR(100),
          purchased_project VARCHAR(200),
          postpartum_months DECIMAL(4,1),
          parity VARCHAR(10),
          delivery_method VARCHAR(20),
          breastfeeding VARCHAR(20),
          lochia_period VARCHAR(50),
          pregnancy_weight DECIMAL(5,1),
          current_weight DECIMAL(5,1),
          body_concerns VARCHAR(500),
          diastasis_recti VARCHAR(50),
          urine_leakage VARCHAR(100),
          pubic_lumbago VARCHAR(100),
          prev_repair_exp VARCHAR(500),
          postpartum_check VARCHAR(200),
          exercise_habits VARCHAR(200),
          intent_level VARCHAR(20),
          worries VARCHAR(500),
          customer_stage VARCHAR(50),
          last_followup_at DATETIME,
          followup_notes VARCHAR(1000),
          next_followup_at DATETIME,
          next_followup_dir VARCHAR(200),
          appointment_date DATE,
          appointment_store VARCHAR(100),
          appointment_item VARCHAR(100),
          arrived VARCHAR(10),
          source_table VARCHAR(100),
          source_row_id VARCHAR(100),
          synced_at DATETIME,
          version INT NOT NULL DEFAULT 0,
          created_at DATETIME NOT NULL,
          updated_at DATETIME NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE tag_categories (
          id BIGINT PRIMARY KEY,
          category_key VARCHAR(50) NOT NULL,
          category_name VARCHAR(100) NOT NULL,
          is_enabled TINYINT NOT NULL,
          merged_into_id BIGINT,
          use_for_filter TINYINT NOT NULL,
          sort_order INT NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE tag_values (
          id BIGINT PRIMARY KEY,
          category_id BIGINT NOT NULL,
          tag_value VARCHAR(50) NOT NULL,
          display_name VARCHAR(100) NOT NULL,
          is_enabled TINYINT NOT NULL,
          merged_into_id BIGINT,
          sort_order INT NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE customer_tag_assignments (
          id BIGINT PRIMARY KEY,
          customer_id BIGINT NOT NULL,
          category_id BIGINT NOT NULL,
          tag_value_id BIGINT NOT NULL,
          is_active TINYINT NOT NULL
        )
        """);
    repository = new CustomerAdminSearchRepository(jdbcTemplate, new CustomerFilterQueryBuilder());

    jdbcTemplate.update("""
        INSERT INTO customers (
          id, phone, nickname, assigned_keeper, source_table, source_row_id, created_at, updated_at
        ) VALUES
        (1, '13800000001', 'Alice', 'keeper-1', 'customers', 'row-1', '2026-07-16 10:00:00', '2026-07-16 10:00:00'),
          (2, '13800000002', 'Bob', 'keeper-1', 'customers', 'row-2', '2026-07-16 11:00:00', '2026-07-16 11:00:00'),
          (3, '13800000003', 'Carol', 'keeper-2', 'customers', 'row-3', '2026-07-16 12:00:00', '2026-07-16 12:00:00')
        """);
    jdbcTemplate.update("""
        INSERT INTO tag_categories (
          id, category_key, category_name, is_enabled, merged_into_id, use_for_filter, sort_order
        ) VALUES (7, 'body_concerns', '身体关注', 1, NULL, 1, 1)
        """);
    jdbcTemplate.update("""
        INSERT INTO tag_values (
          id, category_id, tag_value, display_name, is_enabled, merged_into_id, sort_order
        ) VALUES
          (101, 7, 'DIASTASIS', '腹直肌分离', 1, NULL, 1),
          (102, 7, 'LEAKAGE', '漏尿', 1, NULL, 2)
        """);
    jdbcTemplate.update("""
        INSERT INTO customer_tag_assignments (
          id, customer_id, category_id, tag_value_id, is_active
        ) VALUES
          (1001, 1, 7, 101, 1),
          (1002, 1, 7, 102, 1),
          (1003, 2, 7, 101, 1),
          (1004, 2, 7, 102, 0),
          (1005, 3, 7, 101, 1),
          (1006, 3, 7, 102, 1)
        """);
  }

  @Test
  void allTagFilterKeepsCountPageAndCurrentTagSummariesConsistent() {
    CustomerFilter filter = new CustomerFilter(
        "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        null, null,
        List.of(new TagFilterGroup(7L, List.of(101L, 102L), TagMatchMode.ALL)),
        TagGroupLogic.AND, CustomerSortField.UPDATED_AT, SortDirection.DESC, 1, 20);

    CustomerAdminSearchPage page = repository.search(filter, CustomerAccessScope.all());

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items()).extracting(CustomerAdminListItem::id).containsExactly(3L, 1L);
    assertThat(page.items()).allSatisfy(item -> {
      assertThat(item.tags()).extracting(CustomerTagSummary::valueId)
          .containsExactly(101L, 102L);
    });
  }

  @Test
  void exportRowsUsesTheSameTagFilterAndAccessScopeAsSearch() {
    CustomerFilter filter = new CustomerFilter(
        "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        null, null,
        List.of(new TagFilterGroup(7L, List.of(101L, 102L), TagMatchMode.ALL)),
        TagGroupLogic.AND, CustomerSortField.UPDATED_AT, SortDirection.DESC, 1, 20);
    CustomerAccessScope scope = new CustomerAccessScope(false, List.of("keeper-1"), true);

    CustomerAdminSearchPage page = repository.search(filter, scope);
    List<CustomerAdminListItem> exported = repository.exportRows(filter, scope, 100);

    assertThat(repository.count(filter, scope)).isEqualTo(page.total());
    assertThat(exported).extracting(CustomerAdminListItem::id).containsExactly(1L);
    assertThat(exported.get(0).tags()).extracting(CustomerTagSummary::valueId)
        .containsExactly(101L, 102L);
  }
}
