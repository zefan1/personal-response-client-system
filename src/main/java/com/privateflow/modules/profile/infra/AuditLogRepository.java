package com.privateflow.modules.profile.infra;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

  private final JdbcTemplate jdbcTemplate;

  public AuditLogRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Async("profileAuditExecutor")
  public void log(String action, String operator, String targetType, String targetId, String detail) {
    jdbcTemplate.update("""
        INSERT INTO audit_logs (action, operator, target_type, target_id, detail)
        VALUES (?, ?, ?, ?, ?)
        """,
        action,
        operator == null || operator.isBlank() ? "SYSTEM" : operator,
        targetType,
        targetId,
        detail == null ? null : (detail.length() > 1000 ? detail.substring(0, 1000) : detail));
  }
}
