package com.privateflow.modules.tablewrite.infra;

import com.privateflow.modules.tablewrite.PendingTableWrite;
import com.privateflow.modules.tablewrite.TableWriteActionType;
import com.privateflow.modules.tablewrite.TableWriteStatus;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PendingTableWriteRepository {

  private final JdbcTemplate jdbcTemplate;

  public PendingTableWriteRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void enqueue(String phone, TableWriteActionType actionType, String payload, LocalDateTime nextRetryAt, String errorMsg) {
    jdbcTemplate.update("""
        INSERT INTO pending_table_writes (phone, action_type, payload, retry_count, status, next_retry_at, error_msg)
        VALUES (?, ?, ?, 0, 'PENDING', ?, ?)
        """, phone, actionType.name(), payload, Timestamp.valueOf(nextRetryAt), trim(errorMsg));
  }

  public List<PendingTableWrite> due(int limit) {
    return jdbcTemplate.query("""
        SELECT id, phone, action_type, payload, retry_count, status, next_retry_at, error_msg
        FROM pending_table_writes
        WHERE status = 'PENDING' AND next_retry_at <= NOW()
        ORDER BY next_retry_at ASC
        LIMIT ?
        """, (rs, rowNum) -> {
          PendingTableWrite item = new PendingTableWrite();
          item.setId(rs.getLong("id"));
          item.setPhone(rs.getString("phone"));
          item.setActionType(TableWriteActionType.valueOf(rs.getString("action_type")));
          item.setPayload(rs.getString("payload"));
          item.setRetryCount(rs.getInt("retry_count"));
          item.setStatus(TableWriteStatus.valueOf(rs.getString("status")));
          item.setNextRetryAt(rs.getTimestamp("next_retry_at").toLocalDateTime());
          item.setErrorMsg(rs.getString("error_msg"));
          return item;
        }, limit);
  }

  public void markResolved(long id) {
    jdbcTemplate.update("UPDATE pending_table_writes SET status = 'RESOLVED', updated_at = NOW() WHERE id = ?", id);
  }

  public void markRetry(long id, int retryCount, LocalDateTime nextRetryAt, String errorMsg) {
    jdbcTemplate.update("""
        UPDATE pending_table_writes
        SET retry_count = ?, next_retry_at = ?, error_msg = ?, updated_at = NOW()
        WHERE id = ?
        """, retryCount, Timestamp.valueOf(nextRetryAt), trim(errorMsg), id);
  }

  public void markFailed(long id, int retryCount, String errorMsg) {
    jdbcTemplate.update("""
        UPDATE pending_table_writes
        SET retry_count = ?, status = 'FAILED', error_msg = ?, updated_at = NOW()
        WHERE id = ?
        """, retryCount, trim(errorMsg), id);
  }

  public int countPending() {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM pending_table_writes WHERE status = 'PENDING'",
        Integer.class);
    return count == null ? 0 : count;
  }

  public int countStaleFailed(int alertFailureHours) {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM pending_table_writes
        WHERE status = 'FAILED' AND created_at <= DATE_SUB(NOW(), INTERVAL ? HOUR)
        """, Integer.class, alertFailureHours);
    return count == null ? 0 : count;
  }

  private String trim(String value) {
    if (value == null) {
      return null;
    }
    return value.length() <= 500 ? value : value.substring(0, 500);
  }
}
