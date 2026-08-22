package com.privateflow.modules.tablewrite.infra;

import com.privateflow.modules.tablewrite.PendingTableWrite;
import com.privateflow.modules.tablewrite.TableWriteActionType;
import com.privateflow.modules.tablewrite.TableWriteStatus;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PendingTableWriteRepository {

  private final JdbcTemplate jdbcTemplate;

  public PendingTableWriteRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void enqueue(String phone, TableWriteActionType actionType, String payload, LocalDateTime nextRetryAt, String errorMsg) {
    enqueue(null, phone, actionType, payload, nextRetryAt, errorMsg);
  }

  public void enqueue(Long customerId, String phone, TableWriteActionType actionType, String payload, LocalDateTime nextRetryAt, String errorMsg) {
    jdbcTemplate.update("""
        INSERT INTO pending_table_writes (customer_id, phone, action_type, payload, retry_count, status, next_retry_at, error_msg)
        VALUES (?, ?, ?, ?, 0, 'PENDING', ?, ?)
        """, customerId, phone, actionType.name(), payload, Timestamp.valueOf(nextRetryAt), trim(errorMsg));
  }

  public List<PendingTableWrite> due(int limit) {
    return jdbcTemplate.query("""
        SELECT id, customer_id, phone, action_type, payload, retry_count, status, next_retry_at, error_msg, created_at, updated_at
        FROM pending_table_writes
        WHERE status = 'PENDING' AND next_retry_at <= NOW()
        ORDER BY next_retry_at ASC
        LIMIT ?
        """, (rs, rowNum) -> map(rs), limit);
  }

  public List<PendingTableWrite> failed(int limit) {
    return jdbcTemplate.query("""
        SELECT id, customer_id, phone, action_type, payload, retry_count, status, next_retry_at, error_msg, created_at, updated_at
        FROM pending_table_writes
        WHERE status = 'FAILED'
        ORDER BY updated_at DESC, id DESC
        LIMIT ?
        """, (rs, rowNum) -> map(rs), limit);
  }

  public Optional<PendingTableWrite> findFailed(long id) {
    List<PendingTableWrite> items = jdbcTemplate.query("""
        SELECT id, customer_id, phone, action_type, payload, retry_count, status, next_retry_at, error_msg, created_at, updated_at
        FROM pending_table_writes
        WHERE id = ? AND status = 'FAILED'
        """, (rs, rowNum) -> map(rs), id);
    return items.stream().findFirst();
  }

  /**
   * The original error is retained for operator review and audit. Only a terminal FAILED item
   * can be returned to the retry queue, which makes duplicate clicks and stale pages harmless.
   */
  public int requeueFailed(long id, LocalDateTime nextRetryAt) {
    return jdbcTemplate.update("""
        UPDATE pending_table_writes
        SET status = 'PENDING', retry_count = 0, next_retry_at = ?, updated_at = NOW()
        WHERE id = ? AND status = 'FAILED'
        """, Timestamp.valueOf(nextRetryAt), id);
  }

  /**
   * Closes a terminal failure without deleting its payload or original error.
   * The status predicate makes repeated clicks and stale admin pages harmless.
   */
  public int resolveFailed(long id) {
    return jdbcTemplate.update("""
        UPDATE pending_table_writes
        SET status = 'RESOLVED', updated_at = NOW()
        WHERE id = ? AND status = 'FAILED'
        """, id);
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

  private PendingTableWrite map(java.sql.ResultSet rs) throws java.sql.SQLException {
    PendingTableWrite item = new PendingTableWrite();
    item.setId(rs.getLong("id"));
    long customerId = rs.getLong("customer_id");
    item.setCustomerId(rs.wasNull() ? null : customerId);
    item.setPhone(rs.getString("phone"));
    item.setActionType(TableWriteActionType.valueOf(rs.getString("action_type")));
    item.setPayload(rs.getString("payload"));
    item.setRetryCount(rs.getInt("retry_count"));
    item.setStatus(TableWriteStatus.valueOf(rs.getString("status")));
    Timestamp nextRetryAt = rs.getTimestamp("next_retry_at");
    item.setNextRetryAt(nextRetryAt == null ? null : nextRetryAt.toLocalDateTime());
    item.setErrorMsg(rs.getString("error_msg"));
    Timestamp createdAt = rs.getTimestamp("created_at");
    item.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
    Timestamp updatedAt = rs.getTimestamp("updated_at");
    item.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
    return item;
  }
}
