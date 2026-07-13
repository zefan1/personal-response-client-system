package com.privateflow.modules.api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AuditLogRepositoryTest {

  private JdbcTemplate jdbcTemplate;
  private AuditLogRepository repository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:audit_logs;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS audit_logs");
    jdbcTemplate.execute("DROP TABLE IF EXISTS audit_log_exports");
    jdbcTemplate.execute("""
        CREATE TABLE audit_logs (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          action VARCHAR(50) NOT NULL,
          operator VARCHAR(50) NOT NULL,
          target_type VARCHAR(50) DEFAULT NULL,
          target_id VARCHAR(100) DEFAULT NULL,
          detail TEXT DEFAULT NULL,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE audit_log_exports (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          export_id VARCHAR(64) NOT NULL,
          status VARCHAR(20) NOT NULL,
          filters_json TEXT DEFAULT NULL,
          total_count BIGINT DEFAULT 0,
          csv_content MEDIUMTEXT DEFAULT NULL,
          download_url VARCHAR(500) DEFAULT NULL,
          message VARCHAR(500) DEFAULT NULL,
          expire_at DATETIME NOT NULL,
          created_by VARCHAR(50) DEFAULT NULL,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          completed_at DATETIME DEFAULT NULL
        )
        """);
    jdbcTemplate.update("""
        INSERT INTO audit_logs (action, operator, target_type, target_id, detail, created_at)
        VALUES ('CREATE_NOTICE', 'admin', 'notice', 'notice-1', '{"title":"hello"}', '2026-07-03 12:00:00')
        """);
    repository = new AuditLogRepository(jdbcTemplate);
  }

  @Test
  void keywordSearchMatchesTargetIdentityAndDetail() {
    AuditLogQuery targetQuery = new AuditLogQuery(
        List.of(),
        null,
        null,
        null,
        "notice-1",
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 7),
        1,
        20);

    AuditLogQuery detailQuery = new AuditLogQuery(
        List.of(),
        null,
        null,
        null,
        "hello",
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 7),
        1,
        20);
    AuditLogQuery operatorQuery = new AuditLogQuery(
        List.of(),
        null,
        null,
        null,
        "admin",
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 7),
        1,
        20);

    assertThat(repository.list(targetQuery)).hasSize(1);
    assertThat(repository.list(detailQuery)).hasSize(1);
    assertThat(repository.list(operatorQuery)).hasSize(1);
  }
}
