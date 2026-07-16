package com.privateflow.modules.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.privateflow.modules.customer.admin.CustomerAccessScope;
import com.privateflow.modules.customer.admin.CustomerFilter;
import com.privateflow.modules.customer.admin.CustomerFilterQueryBuilder;
import com.privateflow.modules.customer.admin.CustomerQuerySpec;
import com.privateflow.modules.customer.admin.CustomerSortField;
import com.privateflow.modules.customer.admin.SortDirection;
import com.privateflow.modules.customer.admin.TagFilterGroup;
import com.privateflow.modules.customer.admin.TagGroupLogic;
import com.privateflow.modules.customer.admin.TagMatchMode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class TagAnalyticsRepositoryTest {

  private JdbcTemplate jdbcTemplate;
  private TagAnalyticsRepository repository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:tag_analytics;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    for (String table : List.of(
        "system_tag_suggestions", "unmatched_legacy_tag_values", "tag_analysis_results",
        "tag_analysis_runs", "customer_tag_assignments", "tag_values", "tag_categories",
        "accounts", "customers")) {
      jdbcTemplate.execute("DROP TABLE IF EXISTS " + table);
    }
    jdbcTemplate.execute("""
        CREATE TABLE customers (
          id BIGINT PRIMARY KEY,
          phone VARCHAR(20) NOT NULL,
          nickname VARCHAR(100),
          source_channel VARCHAR(50),
          lead_type VARCHAR(20),
          assigned_keeper VARCHAR(50),
          intended_store VARCHAR(100),
          intended_project VARCHAR(100),
          customer_stage VARCHAR(50),
          created_at DATETIME NOT NULL,
          updated_at DATETIME NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE accounts (
          id BIGINT PRIMARY KEY,
          phone VARCHAR(50),
          username VARCHAR(50) NOT NULL,
          display_name VARCHAR(100) NOT NULL,
          role VARCHAR(20) NOT NULL,
          leader_id BIGINT,
          is_enabled TINYINT NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE tag_categories (
          id BIGINT PRIMARY KEY,
          category_key VARCHAR(50) NOT NULL,
          category_name VARCHAR(100) NOT NULL,
          bound_field VARCHAR(50),
          is_enabled TINYINT NOT NULL,
          merged_into_id BIGINT,
          use_for_filter TINYINT NOT NULL,
          use_for_statistics TINYINT NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE tag_values (
          id BIGINT PRIMARY KEY,
          category_id BIGINT NOT NULL,
          tag_value VARCHAR(50) NOT NULL,
          display_name VARCHAR(100) NOT NULL,
          is_enabled TINYINT NOT NULL,
          merged_into_id BIGINT
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE customer_tag_assignments (
          id BIGINT PRIMARY KEY,
          customer_id BIGINT NOT NULL,
          category_id BIGINT NOT NULL,
          tag_value_id BIGINT NOT NULL,
          is_active TINYINT NOT NULL,
          source_type VARCHAR(32) NOT NULL,
          created_at DATETIME NOT NULL,
          invalidated_at DATETIME
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE tag_analysis_runs (
          id BIGINT PRIMARY KEY,
          customer_id BIGINT NOT NULL,
          status VARCHAR(20) NOT NULL,
          error_message VARCHAR(1000),
          finished_at DATETIME,
          created_at DATETIME NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE tag_analysis_results (
          id BIGINT PRIMARY KEY,
          analysis_run_id BIGINT NOT NULL,
          category_id BIGINT NOT NULL,
          tag_value_id BIGINT,
          result_type VARCHAR(24) NOT NULL,
          requested_action VARCHAR(20) NOT NULL,
          validation_status VARCHAR(20) NOT NULL,
          validation_reason VARCHAR(1000)
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE unmatched_legacy_tag_values (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          customer_id BIGINT NOT NULL,
          status VARCHAR(20) NOT NULL,
          raw_value VARCHAR(500)
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE system_tag_suggestions (
          id BIGINT PRIMARY KEY,
          customer_id BIGINT,
          tag_value_id BIGINT,
          status VARCHAR(20) NOT NULL
        )
        """);
    repository = new TagAnalyticsRepository(jdbcTemplate);
  }

  @Test
  void snapshotCountsOnlyCurrentEnabledUnmergedStatisticsTags() {
    seedCustomer(1, "企微", "万江店", "keeper-1", "2026-07-16 10:00:00");
    seedCustomer(2, "抖音", "南城店", "keeper-2", "2026-07-16 11:00:00");
    seedCategory(10, "intent_level", "意向等级", "intentLevel", 1, null, 1);
    seedValue(101, 10, "HIGH", "高意向", 1, null);
    seedValue(102, 10, "LOW", "低意向", 0, null);
    seedAssignment(1001, 1, 10, 101, 1, "SYSTEM_INFERENCE", "2026-07-12 09:00:00", null);
    seedAssignment(1002, 2, 10, 101, 0, "MANUAL", "2026-07-11 09:00:00", "2026-07-13 09:00:00");
    seedAssignment(1003, 2, 10, 102, 1, "MANUAL", "2026-07-14 09:00:00", null);
    jdbcTemplate.update("INSERT INTO system_tag_suggestions (id, customer_id, tag_value_id, status) VALUES (1, 2, 101, 'PENDING')");

    TagAnalyticsResponse response = repository.analyze(allSpec(), allSpec(), window());

    assertThat(response.summary().matchedCustomerCount()).isEqualTo(2);
    assertThat(response.summary().taggedCustomerCount()).isEqualTo(1);
    assertThat(response.summary().activeAssignmentCount()).isEqualTo(1);
    assertThat(response.summary().coverageRate()).isEqualTo(0.5);
    assertThat(response.categories()).singleElement().satisfies(row -> {
      assertThat(row.categoryKey()).isEqualTo("intent_level");
      assertThat(row.activeAssignmentCount()).isEqualTo(1);
    });
    assertThat(response.tags()).singleElement().satisfies(row -> {
      assertThat(row.valueCode()).isEqualTo("HIGH");
      assertThat(row.displayName()).isEqualTo("高意向");
    });
  }

  @Test
  void consumesTheSameStructuredCustomerQuerySpecAsSearch() {
    seedCustomer(1, "企微", "万江店", "keeper-1", "2026-07-16 10:00:00");
    seedCustomer(2, "抖音", "万江店", "keeper-1", "2026-07-16 10:00:00");
    seedCategory(10, "intent_level", "意向等级", "intentLevel", 1, null, 1);
    seedValue(101, 10, "HIGH", "高意向", 1, null);
    seedAssignment(1001, 1, 10, 101, 1, "SYSTEM_INFERENCE", "2026-07-12 09:00:00", null);
    seedAssignment(1002, 2, 10, 101, 1, "SYSTEM_INFERENCE", "2026-07-12 09:00:00", null);
    CustomerFilter filter = new CustomerFilter(
        "", List.of("企微"), List.of(), List.of(), List.of(), List.of(), List.of(),
        null, null,
        List.of(new TagFilterGroup(10L, List.of(101L), TagMatchMode.ANY)),
        TagGroupLogic.AND, CustomerSortField.UPDATED_AT, SortDirection.DESC, 1, 20);
    CustomerQuerySpec spec = new CustomerFilterQueryBuilder().build(filter, CustomerAccessScope.all());

    TagAnalyticsResponse response = repository.analyze(spec, allSpec(), window());

    assertThat(response.summary().matchedCustomerCount()).isEqualTo(1);
    assertThat(response.summary().activeAssignmentCount()).isEqualTo(1);
  }

  @Test
  void groupsCurrentTagsByStoreTeamAndEmployeeAndResolvesTeamKeepers() {
    seedAccount(9, "leader-1", "组长一", "LEADER", null, 1);
    seedAccount(11, "keeper-1", "管家一", "KEEPER", 9L, 1);
    seedAccount(12, "keeper-disabled", "停用管家", "KEEPER", 9L, 0);
    seedCustomer(1, "企微", "万江店", "keeper-1", "2026-07-16 10:00:00");
    seedCategory(10, "intent_level", "意向等级", "intentLevel", 1, null, 1);
    seedValue(101, 10, "HIGH", "高意向", 1, null);
    seedAssignment(1001, 1, 10, 101, 1, "SYSTEM_INFERENCE", "2026-07-12 09:00:00", null);

    TagAnalyticsResponse response = repository.analyze(allSpec(), allSpec(), window());

    assertThat(repository.resolveEnabledKeeperPhones(List.of(9L))).containsExactly("keeper-1");
    assertThat(response.stores()).singleElement().satisfies(row -> {
      assertThat(row.key()).isEqualTo("万江店");
      assertThat(row.label()).isEqualTo("万江店");
    });
    assertThat(response.teams()).singleElement().satisfies(row -> {
      assertThat(row.key()).isEqualTo("9");
      assertThat(row.label()).isEqualTo("组长一");
    });
    assertThat(response.employees()).singleElement().satisfies(row -> {
      assertThat(row.key()).isEqualTo("keeper-1");
      assertThat(row.label()).isEqualTo("管家一");
    });
    assertThat(response.filterOptions().employees())
        .extracting(TagAnalyticsResponse.EmployeeOption::account)
        .containsExactly("keeper-1");
    assertThat(response.filterOptions().teams())
        .extracting(TagAnalyticsResponse.TeamOption::leaderId)
        .containsExactly(9L);
  }

  @Test
  void aggregatesAddedInvalidatedAndNetTrendInsideEventWindow() {
    seedCustomer(1, "企微", "万江店", "keeper-1", "2026-07-16 10:00:00");
    seedCategory(10, "intent_level", "意向等级", "intentLevel", 1, null, 1);
    seedValue(101, 10, "HIGH", "高意向", 1, null);
    seedAssignment(1001, 1, 10, 101, 0, "SYSTEM_INFERENCE", "2026-07-11 09:00:00", "2026-07-13 09:00:00");
    seedAssignment(1002, 1, 10, 101, 1, "MANUAL", "2026-07-14 09:00:00", null);
    jdbcTemplate.update("INSERT INTO system_tag_suggestions (id, customer_id, tag_value_id, status) VALUES (2, 1, 101, 'PENDING')");

    TagAnalyticsResponse response = repository.analyze(allSpec(), allSpec(), window());

    assertThat(response.summary().systemAddedCount()).isEqualTo(1);
    assertThat(response.summary().manualAddedOrChangedCount()).isEqualTo(1);
    assertThat(response.tagSources()).extracting(
        TagAnalyticsResponse.SourceRow::sourceType,
        TagAnalyticsResponse.SourceRow::addedAssignmentCount)
        .containsExactly(tuple("MANUAL", 1L), tuple("SYSTEM_INFERENCE", 1L));
    assertThat(response.trend()).hasSize(7);
    assertThat(response.trend().stream().filter(row -> row.date().toString().equals("2026-07-13")).findFirst())
        .get().satisfies(row -> {
          assertThat(row.invalidatedAssignmentCount()).isEqualTo(1);
          assertThat(row.netChange()).isEqualTo(-1);
        });
  }

  private CustomerQuerySpec allSpec() {
    return new CustomerQuerySpec(" WHERE 1=1", List.of(), "c.id ASC");
  }

  private TagAnalyticsWindow window() {
    return new TagAnalyticsWindow(
        LocalDateTime.of(2026, 7, 10, 0, 0),
        LocalDateTime.of(2026, 7, 16, 23, 59, 59),
        TagTrendGranularity.DAY);
  }

  private void seedCustomer(long id, String source, String store, String keeper, String updatedAt) {
    jdbcTemplate.update("""
        INSERT INTO customers (
          id, phone, nickname, source_channel, lead_type, assigned_keeper,
          intended_store, intended_project, customer_stage, created_at, updated_at
        ) VALUES (?, ?, ?, ?, 'XIAN_SUO', ?, ?, '产后修复', 'PENDING', '2026-07-01 09:00:00', ?)
        """, id, "1380000" + String.format("%04d", id), "客户" + id, source, keeper, store, updatedAt);
  }

  private void seedAccount(long id, String account, String displayName, String role, Long leaderId, int enabled) {
    jdbcTemplate.update("""
        INSERT INTO accounts (id, phone, username, display_name, role, leader_id, is_enabled)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """, id, account, account, displayName, role, leaderId, enabled);
  }

  private void seedCategory(long id, String key, String name, String boundField, int enabled, Long mergedIntoId, int useForStatistics) {
    jdbcTemplate.update("""
        INSERT INTO tag_categories (
          id, category_key, category_name, bound_field, is_enabled,
          merged_into_id, use_for_filter, use_for_statistics
        ) VALUES (?, ?, ?, ?, ?, ?, 1, ?)
        """, id, key, name, boundField, enabled, mergedIntoId, useForStatistics);
  }

  private void seedValue(long id, long categoryId, String code, String displayName, int enabled, Long mergedIntoId) {
    jdbcTemplate.update("""
        INSERT INTO tag_values (
          id, category_id, tag_value, display_name, is_enabled, merged_into_id
        ) VALUES (?, ?, ?, ?, ?, ?)
        """, id, categoryId, code, displayName, enabled, mergedIntoId);
  }

  private void seedAssignment(long id, long customerId, long categoryId, long valueId, int active, String sourceType, String createdAt, String invalidatedAt) {
    jdbcTemplate.update("""
        INSERT INTO customer_tag_assignments (
          id, customer_id, category_id, tag_value_id, is_active,
          source_type, created_at, invalidated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, id, customerId, categoryId, valueId, active, sourceType, createdAt, invalidatedAt);
  }
}
