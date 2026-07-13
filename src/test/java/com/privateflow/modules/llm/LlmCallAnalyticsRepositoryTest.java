package com.privateflow.modules.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LlmCallAnalyticsRepositoryTest {

  private JdbcTemplate jdbcTemplate;
  private LlmCallAnalyticsRepository repository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:llm_call_analytics;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS llm_call_logs");
    jdbcTemplate.execute("""
        CREATE TABLE llm_call_logs (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          scene VARCHAR(50) DEFAULT NULL,
          lead_type VARCHAR(20) DEFAULT NULL,
          caller VARCHAR(50) DEFAULT NULL,
          route_id BIGINT DEFAULT NULL,
          llm_environment_id BIGINT DEFAULT NULL,
          model VARCHAR(100) DEFAULT NULL,
          protocol VARCHAR(50) DEFAULT NULL,
          request_summary TEXT DEFAULT NULL,
          response_time INT DEFAULT NULL,
          success TINYINT NOT NULL DEFAULT 0,
          error_code VARCHAR(50) DEFAULT NULL,
          error_msg VARCHAR(500) DEFAULT NULL,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    repository = new LlmCallAnalyticsRepository(jdbcTemplate);
  }

  @Test
  void summarizesLlmCallsBySceneLeadTypeAndEnvironment() {
    insert("REPLY_GENERATION", "TUAN_GOU", 1L, "gpt-a", 100, true);
    insert("REPLY_GENERATION", "TUAN_GOU", 1L, "gpt-a", 300, false);
    insert("SUMMARY", "PENDING", 2L, "gpt-b", 50, true);

    LlmCallAnalytics analytics = repository.query(7, null, null);

    assertThat(analytics.summary().totalCalls()).isEqualTo(3);
    assertThat(analytics.summary().successRate()).isEqualTo(2.0 / 3.0);
    assertThat(analytics.summary().avgResponseTime()).isEqualTo(150L);
    assertThat(analytics.details()).hasSize(2);
    assertThat(analytics.details().get(0).scene()).isEqualTo("REPLY_GENERATION");
    assertThat(analytics.details().get(0).totalCalls()).isEqualTo(2);
    assertThat(analytics.details().get(0).failCount()).isEqualTo(1);
  }

  private void insert(String scene, String leadType, Long environmentId, String model, int responseTime, boolean success) {
    jdbcTemplate.update("""
        INSERT INTO llm_call_logs
          (scene, lead_type, caller, llm_environment_id, model, protocol, request_summary, response_time, success, created_at)
        VALUES (?, ?, 'tester', ?, ?, 'OPENAI_COMPATIBLE', 'summary', ?, ?, NOW())
        """, scene, leadType, environmentId, model, responseTime, success ? 1 : 0);
  }
}
