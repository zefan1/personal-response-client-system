package com.privateflow.modules.llm;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PendingFollowupAnalysisRepository {

  private final JdbcTemplate jdbcTemplate;

  public PendingFollowupAnalysisRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void enqueue(String requestKey, String phone, String payload, LocalDateTime nextRetryAt, String errorMsg) {
    jdbcTemplate.update("""
        INSERT INTO pending_followup_analyses
          (request_key, phone, payload, retry_count, status, next_retry_at, error_msg)
        VALUES (?, ?, ?, 0, 'PENDING', ?, ?)
        ON DUPLICATE KEY UPDATE updated_at = NOW()
        """, requestKey, phone, payload, Timestamp.valueOf(nextRetryAt), trim(errorMsg));
  }

  public List<PendingFollowupAnalysis> due(int limit) {
    return jdbcTemplate.query("""
        SELECT id, phone, payload, retry_count, status, next_retry_at, error_msg
        FROM pending_followup_analyses
        WHERE status = 'PENDING' AND next_retry_at <= NOW()
        ORDER BY next_retry_at ASC
        LIMIT ?
        """, (rs, rowNum) -> {
          PendingFollowupAnalysis item = new PendingFollowupAnalysis();
          item.setId(rs.getLong("id"));
          item.setPhone(rs.getString("phone"));
          item.setPayload(rs.getString("payload"));
          item.setRetryCount(rs.getInt("retry_count"));
          item.setStatus(rs.getString("status"));
          item.setNextRetryAt(rs.getTimestamp("next_retry_at").toLocalDateTime());
          item.setErrorMsg(rs.getString("error_msg"));
          return item;
        }, limit);
  }

  public void markResolved(long id) {
    jdbcTemplate.update(
        "UPDATE pending_followup_analyses SET status = 'RESOLVED', updated_at = NOW() WHERE id = ?", id);
  }

  public void markRetry(long id, int retryCount, LocalDateTime nextRetryAt, String errorMsg) {
    jdbcTemplate.update("""
        UPDATE pending_followup_analyses
        SET retry_count = ?, next_retry_at = ?, error_msg = ?, updated_at = NOW()
        WHERE id = ?
        """, retryCount, Timestamp.valueOf(nextRetryAt), trim(errorMsg), id);
  }

  public void markFailed(long id, int retryCount, String errorMsg) {
    jdbcTemplate.update("""
        UPDATE pending_followup_analyses
        SET retry_count = ?, status = 'FAILED', error_msg = ?, updated_at = NOW()
        WHERE id = ?
        """, retryCount, trim(errorMsg), id);
  }

  private String trim(String value) {
    if (value == null) {
      return null;
    }
    return value.length() <= 500 ? value : value.substring(0, 500);
  }
}
