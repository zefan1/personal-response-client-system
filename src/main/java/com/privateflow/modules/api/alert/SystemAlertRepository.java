package com.privateflow.modules.api.alert;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SystemAlertRepository {

  private final JdbcTemplate jdbcTemplate;

  public SystemAlertRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void activate(String alertType, String level, String message, String sourceModule, String detail) {
    jdbcTemplate.update("""
        INSERT INTO system_alerts (alert_type, level, status, message, source_module, detail)
        VALUES (?, ?, 'ACTIVE', ?, ?, ?)
        """, alertType, level, truncate(message, 500), sourceModule, detail);
  }

  public void resolve(String alertType) {
    jdbcTemplate.update("""
        UPDATE system_alerts
        SET status = 'RESOLVED', resolved_at = NOW()
        WHERE alert_type = ? AND status = 'ACTIVE'
        """, alertType);
  }

  public List<Map<String, Object>> activeAlerts() {
    return jdbcTemplate.queryForList("""
        SELECT id, alert_type, level, status, message, source_module, occurred_at, resolved_at
        FROM system_alerts
        WHERE status = 'ACTIVE'
        ORDER BY occurred_at DESC
        LIMIT 100
        """);
  }

  public void cleanupResolved(int retentionDays) {
    jdbcTemplate.update("""
        DELETE FROM system_alerts
        WHERE status = 'RESOLVED' AND resolved_at < DATE_SUB(NOW(), INTERVAL ? DAY)
        """, retentionDays);
  }

  private String truncate(String value, int max) {
    if (value == null) {
      return "";
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}
