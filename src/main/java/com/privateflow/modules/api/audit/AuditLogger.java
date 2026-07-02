package com.privateflow.modules.api.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogger {

  private final JdbcTemplate jdbcTemplate;

  public AuditLogger(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Async("auditLogExecutor")
  public void log(String action, String operator, String targetType, String targetId, String detail) {
    try {
      jdbcTemplate.update("""
          INSERT INTO audit_logs (action, operator, target_type, target_id, detail)
          VALUES (?, ?, ?, ?, ?)
          """,
          action,
          operator == null || operator.isBlank() ? "SYSTEM" : operator,
          targetType,
          targetId,
          detail == null ? null : (detail.length() > 1000 ? detail.substring(0, 1000) : detail));
    } catch (RuntimeException ex) {
      // Audit failures must never block the business request.
    }
  }
}
