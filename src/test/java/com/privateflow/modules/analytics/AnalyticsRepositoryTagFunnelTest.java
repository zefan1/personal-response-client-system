package com.privateflow.modules.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.privateflow.modules.api.Role;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AnalyticsRepositoryTagFunnelTest {

  private JdbcTemplate jdbcTemplate;
  private AnalyticsRepository repository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:analytics_funnel;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
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
          lead_type VARCHAR(20),
          intent_level VARCHAR(20),
          assigned_keeper VARCHAR(50),
          followup_notes VARCHAR(1000),
          purchased_project VARCHAR(200),
          arrived VARCHAR(10),
          customer_stage VARCHAR(50),
          last_followup_at DATETIME,
          next_followup_at DATETIME,
          created_at DATETIME NOT NULL,
          updated_at DATETIME NOT NULL
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
          is_active TINYINT NOT NULL
        )
        """);
    repository = new AnalyticsRepository(jdbcTemplate);
  }

  @Test
  void xianSuoIntentStepsRequireCurrentStatisticsTagAssignment() {
    jdbcTemplate.update("""
        INSERT INTO customers (
          id, phone, lead_type, intent_level, followup_notes, purchased_project,
          arrived, customer_stage, created_at, updated_at
        ) VALUES (1, '13800000001', 'XIAN_SUO', 'HIGH', '已沟通', '修复单',
                  '是', '已到店', '2026-07-01 09:00:00', '2026-07-10 09:00:00')
        """);

    Map<String, Object> before = repository.funnels(
        "XIAN_SUO", new AnalyticsScope(Role.ADMIN, "admin", null));
    assertThat(stageCount(before, "intentConfirmed")).isZero();

    seedIntentDirectoryAndAssignment(1L, 10L, 101L, "HIGH");
    Map<String, Object> after = repository.funnels(
        "XIAN_SUO", new AnalyticsScope(Role.ADMIN, "admin", null));
    assertThat(stageCount(after, "intentConfirmed")).isEqualTo(1);
    assertThat(stageCount(after, "purchased")).isEqualTo(1);

    jdbcTemplate.update("UPDATE tag_values SET is_enabled = 0 WHERE id = 101");
    Map<String, Object> disabled = repository.funnels(
        "XIAN_SUO", new AnalyticsScope(Role.ADMIN, "admin", null));
    assertThat(stageCount(disabled, "intentConfirmed")).isZero();
  }

  @SuppressWarnings("unchecked")
  private long stageCount(Map<String, Object> result, String stageKey) {
    Map<String, Object> xianSuo = (Map<String, Object>) result.get("xianSuo");
    List<Map<String, Object>> stages = (List<Map<String, Object>>) xianSuo.get("stages");
    return stages.stream()
        .filter(stage -> stageKey.equals(stage.get("stageKey")))
        .map(stage -> ((Number) stage.get("count")).longValue())
        .findFirst()
        .orElseThrow();
  }

  private void seedIntentDirectoryAndAssignment(
      long customerId,
      long categoryId,
      long valueId,
      String valueCode) {
    jdbcTemplate.update("""
        INSERT INTO tag_categories (
          id, category_key, category_name, bound_field, is_enabled,
          merged_into_id, use_for_statistics
        ) VALUES (?, 'intent_level', '意向等级', 'intentLevel', 1, NULL, 1)
        """, categoryId);
    jdbcTemplate.update("""
        INSERT INTO tag_values (
          id, category_id, tag_value, display_name, is_enabled, merged_into_id
        ) VALUES (?, ?, ?, '高意向', 1, NULL)
        """, valueId, categoryId, valueCode);
    jdbcTemplate.update("""
        INSERT INTO customer_tag_assignments (
          id, customer_id, category_id, tag_value_id, is_active
        ) VALUES (1001, ?, ?, ?, 1)
        """, customerId, categoryId, valueId);
  }
}
