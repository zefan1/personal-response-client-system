package com.privateflow.modules.tablewrite.callback;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class SmartSheetCallbackInboxRepository {

  private final JdbcTemplate jdbcTemplate;

  SmartSheetCallbackInboxRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  void enqueue(SmartSheetCallbackEvent event, String recordIdsJson) {
    jdbcTemplate.update("""
        INSERT INTO wecom_smartsheet_callback_inbox
          (event_key, table_role, source_table, document_id, sheet_id, change_type, record_ids_json, operator_name, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
        ON DUPLICATE KEY UPDATE event_key = VALUES(event_key)
        """,
        event.eventKey(), event.role(), event.sourceTable(), event.target().documentId(), event.target().sheetId(),
        event.changeType(), recordIdsJson, event.operator());
  }

  List<InboxItem> claimDue(int limit) {
    List<InboxItem> items = jdbcTemplate.query("""
        SELECT id, event_key, table_role, source_table, document_id, sheet_id, change_type,
               record_ids_json, operator_name, attempts
        FROM wecom_smartsheet_callback_inbox
        WHERE status IN ('PENDING', 'RETRY')
          AND next_attempt_at <= CURRENT_TIMESTAMP
        ORDER BY id ASC
        LIMIT ?
        """, (rs, rowNum) -> new InboxItem(
            rs.getLong("id"), rs.getString("event_key"), rs.getString("table_role"),
            rs.getString("source_table"), rs.getString("document_id"), rs.getString("sheet_id"),
            rs.getString("change_type"), rs.getString("record_ids_json"), rs.getString("operator_name"),
            rs.getInt("attempts")), Math.max(1, limit));
    return items.stream().filter(item -> jdbcTemplate.update("""
        UPDATE wecom_smartsheet_callback_inbox
        SET status = 'PROCESSING', attempts = attempts + 1, last_error = NULL, updated_at = CURRENT_TIMESTAMP
        WHERE id = ? AND status IN ('PENDING', 'RETRY')
        """, item.id()) == 1).toList();
  }

  void recoverStaleProcessing() {
    jdbcTemplate.update("""
        UPDATE wecom_smartsheet_callback_inbox
        SET status = 'RETRY', next_attempt_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
        WHERE status = 'PROCESSING' AND updated_at < ?
        """, Timestamp.valueOf(LocalDateTime.now().minusMinutes(5)));
  }

  void resolve(long id) {
    jdbcTemplate.update("""
        UPDATE wecom_smartsheet_callback_inbox
        SET status = 'RESOLVED', resolved_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """, id);
  }

  void ignore(long id, String reason) {
    jdbcTemplate.update("""
        UPDATE wecom_smartsheet_callback_inbox
        SET status = 'IGNORED', last_error = ?, resolved_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """, clipped(reason), id);
  }

  void defer(long id, String reason, LocalDateTime nextAttemptAt) {
    jdbcTemplate.update("""
        UPDATE wecom_smartsheet_callback_inbox
        SET status = 'RETRY', last_error = ?, next_attempt_at = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """, clipped(reason), Timestamp.valueOf(nextAttemptAt), id);
  }

  private static String clipped(String value) {
    if (value == null) {
      return "callback processing failed";
    }
    return value.substring(0, Math.min(1000, value.length()));
  }

  record InboxItem(
      long id,
      String eventKey,
      String role,
      String sourceTable,
      String documentId,
      String sheetId,
      String changeType,
      String recordIdsJson,
      String operator,
      int attempts) {
  }
}
