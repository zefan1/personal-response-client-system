package com.privateflow.modules.customer.admin;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerStageOptionRepository {

  private final JdbcTemplate jdbcTemplate;

  public CustomerStageOptionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<Map<String, Object>> list(String sourceTable, String fieldName) {
    return jdbcTemplate.query("""
        SELECT id, source_table, field_name, option_id, option_text, status,
               first_seen_at, last_seen_at, confirmed_at, confirmed_by
        FROM customer_stage_options
        WHERE source_table = ? AND field_name = ?
        ORDER BY CASE status WHEN 'PENDING' THEN 0 WHEN 'ORPHANED' THEN 1 ELSE 2 END,
                 option_text, id
        """, (rs, rowNum) -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", rs.getLong("id"));
      item.put("sourceTable", rs.getString("source_table"));
      item.put("fieldName", rs.getString("field_name"));
      item.put("optionId", rs.getString("option_id"));
      item.put("optionText", rs.getString("option_text"));
      item.put("status", rs.getString("status"));
      item.put("firstSeenAt", localDateTime(rs.getTimestamp("first_seen_at")));
      item.put("lastSeenAt", localDateTime(rs.getTimestamp("last_seen_at")));
      item.put("confirmedAt", localDateTime(rs.getTimestamp("confirmed_at")));
      item.put("confirmedBy", rs.getString("confirmed_by"));
      return item;
    }, sourceTable, fieldName);
  }

  public Optional<Map<String, Object>> find(String sourceTable, String fieldName, String optionId) {
    return list(sourceTable, fieldName).stream()
        .filter(item -> optionId.equals(item.get("optionId")))
        .findFirst();
  }

  public boolean exists(String sourceTable, String fieldName) {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM customer_stage_options
        WHERE source_table = ? AND field_name = ?
        """, Integer.class, sourceTable, fieldName);
    return count != null && count > 0;
  }

  public boolean hasActive(String sourceTable, String fieldName) {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM customer_stage_options
        WHERE source_table = ? AND field_name = ? AND status = 'ACTIVE'
        """, Integer.class, sourceTable, fieldName);
    return count != null && count > 0;
  }

  public void observe(String sourceTable, String fieldName, String optionId, String optionText, boolean initial) {
    jdbcTemplate.update("""
        INSERT INTO customer_stage_options
          (source_table, field_name, option_id, option_text, status, first_seen_at, last_seen_at)
        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON DUPLICATE KEY UPDATE
          option_text = VALUES(option_text),
          status = CASE WHEN status = 'MIGRATED' THEN 'ACTIVE' ELSE status END,
          last_seen_at = CURRENT_TIMESTAMP,
          updated_at = CURRENT_TIMESTAMP
        """, sourceTable, fieldName, optionId, optionText, initial ? "ACTIVE" : "PENDING");
  }

  public void markMissing(String sourceTable, String fieldName, List<String> activeOptionIds) {
    if (activeOptionIds == null || activeOptionIds.isEmpty()) {
      jdbcTemplate.update("""
          UPDATE customer_stage_options SET status = 'ORPHANED', updated_at = CURRENT_TIMESTAMP
          WHERE source_table = ? AND field_name = ? AND status IN ('ACTIVE', 'PENDING')
          """, sourceTable, fieldName);
      return;
    }
    String placeholders = String.join(",", activeOptionIds.stream().map(ignored -> "?").toList());
    String sql = """
        UPDATE customer_stage_options SET status = 'ORPHANED', updated_at = CURRENT_TIMESTAMP
        WHERE source_table = ? AND field_name = ? AND status IN ('ACTIVE', 'PENDING')
          AND option_id NOT IN (""" + placeholders + ")";
    Object[] args = new Object[2 + activeOptionIds.size()];
    args[0] = sourceTable;
    args[1] = fieldName;
    for (int i = 0; i < activeOptionIds.size(); i++) {
      args[i + 2] = activeOptionIds.get(i);
    }
    jdbcTemplate.update(sql, args);
  }

  public void confirm(String sourceTable, String fieldName, String optionId, String status, String operator) {
    jdbcTemplate.update("""
        UPDATE customer_stage_options
        SET status = ?, confirmed_at = CURRENT_TIMESTAMP, confirmed_by = ?, updated_at = CURRENT_TIMESTAMP
        WHERE source_table = ? AND field_name = ? AND option_id = ?
        """, status, operator, sourceTable, fieldName, optionId);
  }

  private LocalDateTime localDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
