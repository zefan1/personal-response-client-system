package com.privateflow.modules.api.chat;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SendConfirmationRepository {

  private final JdbcTemplate jdbcTemplate;

  public SendConfirmationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean claim(String confirmationId, String operator, String phone) {
    int inserted = jdbcTemplate.update("""
        INSERT IGNORE INTO send_confirmations (operator, confirmation_id, phone)
        VALUES (?, ?, ?)
        """, operator, confirmationId, phone);
    if (inserted == 1) {
      return true;
    }
    String existingPhone = jdbcTemplate.queryForObject("""
        SELECT phone FROM send_confirmations
        WHERE operator = ? AND confirmation_id = ?
        """, String.class, operator, confirmationId);
    if (phone.equals(existingPhone)) {
      return false;
    }
    throw new ApiException(ApiErrorCodes.BAD_REQUEST, "确认编号已用于其他客户，请重新复制回复");
  }

  public void registerPending(PendingSendRequest request, String operator) {
    jdbcTemplate.update("""
        INSERT INTO pending_send_confirmation_state
          (confirmation_id, operator, customer_id, phone, nickname, copied_text, reply_source, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, 'AWAITING_DECISION')
        ON DUPLICATE KEY UPDATE
          customer_id = VALUES(customer_id), phone = VALUES(phone), nickname = VALUES(nickname),
          copied_text = VALUES(copied_text), reply_source = VALUES(reply_source),
          status = 'AWAITING_DECISION', reminder_count = 0, last_reminder_at = NULL
        """,
        request.confirmationId().trim(), operator, request.customerId(), blankToNull(request.phone()),
        blankToNull(request.nickname()), request.copiedText().trim(), blankToNull(request.replySource()));
  }

  public void updatePendingStatus(
      String confirmationId, String operator, String status, int reminderCount) {
    jdbcTemplate.update("""
        UPDATE pending_send_confirmation_state
        SET status = ?, reminder_count = ?,
            last_reminder_at = CASE WHEN ? > 0 THEN CURRENT_TIMESTAMP(6) ELSE last_reminder_at END
        WHERE confirmation_id = ? AND operator = ? AND status <> 'SENT'
        """, status, reminderCount, reminderCount, confirmationId.trim(), operator);
  }

  /**
   * A copied reply can be confirmed only while the employee is still deciding. This prevents a
   * stale client request from applying follow-up effects after "未发送" or "重新识别" was chosen.
   */
  public void claimPendingForSend(String confirmationId, String operator) {
    int claimed = jdbcTemplate.update("""
        UPDATE pending_send_confirmation_state
        SET status = 'CONFIRMING'
        WHERE confirmation_id = ? AND operator = ? AND status = 'AWAITING_DECISION'
        """, confirmationId.trim(), operator);
    if (claimed == 1) {
      return;
    }
    List<String> statuses = jdbcTemplate.query(
        "SELECT status FROM pending_send_confirmation_state WHERE confirmation_id = ? AND operator = ?",
        (rs, rowNum) -> rs.getString("status"), confirmationId.trim(), operator);
    if (statuses.isEmpty()) {
      return;
    }
    throw new ApiException(ApiErrorCodes.CONFLICT, "该回复已标记为未发送或重新识别，不能再确认发送");
  }

  public void markPendingSent(String confirmationId, String operator, Long customerId) {
    jdbcTemplate.update("""
        UPDATE pending_send_confirmation_state
        SET status = 'SENT', customer_id = COALESCE(?, customer_id)
        WHERE confirmation_id = ? AND operator = ? AND status = 'CONFIRMING'
        """, customerId, confirmationId.trim(), operator);
  }

  public Map<String, Object> summary(int days, String operator) {
    List<Object> args = new ArrayList<>();
    String where = " WHERE updated_at >= DATE_SUB(NOW(), INTERVAL ? DAY) ";
    args.add(days);
    if (blankToNull(operator) != null) {
      where += " AND operator = ? ";
      args.add(operator.trim());
    }
    return jdbcTemplate.queryForObject("""
        SELECT COUNT(*) AS copied_count,
               SUM(CASE WHEN status = 'AWAITING_DECISION' THEN 1 ELSE 0 END) AS awaiting_count,
               SUM(CASE WHEN status = 'CONFIRMING' THEN 1 ELSE 0 END) AS confirming_count,
               SUM(CASE WHEN status = 'UNSENT' THEN 1 ELSE 0 END) AS unsent_count,
               SUM(CASE WHEN status = 'RECOGNITION_RETRY' THEN 1 ELSE 0 END) AS recognition_retry_count,
               SUM(CASE WHEN status = 'SENT' THEN 1 ELSE 0 END) AS sent_count
        FROM pending_send_confirmation_state
        """ + where, (rs, rowNum) -> {
      long sent = rs.getLong("sent_count");
      long unsent = rs.getLong("unsent_count");
      long retried = rs.getLong("recognition_retry_count");
      long decided = sent + unsent + retried;
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("copiedCount", rs.getLong("copied_count"));
      result.put("awaitingDecisionCount", rs.getLong("awaiting_count"));
      result.put("confirmingCount", rs.getLong("confirming_count"));
      result.put("unsentCount", unsent);
      result.put("recognitionRetryCount", retried);
      result.put("sentCount", sent);
      result.put("decidedCount", decided);
      result.put("confirmedSendRate", decided == 0 ? 0.0 : sent / (double) decided);
      return result;
    }, args.toArray());
  }

  private String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
