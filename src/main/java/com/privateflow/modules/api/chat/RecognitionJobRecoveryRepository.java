package com.privateflow.modules.api.chat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persists only enough state to explain a task interrupted by an application restart. Screenshot
 * bytes, recognition input, and generated replies deliberately remain outside this table.
 */
@Repository
public class RecognitionJobRecoveryRepository {

  static final String BACKEND_RESTARTED = "RECOGNITION_BACKEND_RESTARTED";

  private final JdbcTemplate jdbcTemplate;

  public RecognitionJobRecoveryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void save(RecognitionJobView job, String username) {
    Object[] values = {
        username, job.replySessionId(), job.status().name(), job.errorCode(),
        Timestamp.from(job.updatedAt()), job.jobId()
    };
    if (update(values) == 0) {
      try {
        jdbcTemplate.update("""
            INSERT INTO recognition_job_restart_recovery
                (job_id, username, reply_session_id, status, error_code, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            job.jobId(), username, job.replySessionId(), job.status().name(), job.errorCode(),
            Timestamp.from(job.createdAt()), Timestamp.from(job.updatedAt()));
      } catch (DuplicateKeyException duplicate) {
        update(values);
      }
    }
  }

  private int update(Object[] values) {
    return jdbcTemplate.update("""
        UPDATE recognition_job_restart_recovery
        SET username = ?, reply_session_id = ?, status = ?, error_code = ?, updated_at = ?
        WHERE job_id = ?
        """, values);
  }

  public Optional<RecoveredRecognitionJob> find(String jobId) {
    return jdbcTemplate.query("""
        SELECT job_id, username, reply_session_id, status, error_code, created_at, updated_at
        FROM recognition_job_restart_recovery
        WHERE job_id = ?
        """, (rs, rowNum) -> new RecoveredRecognitionJob(
            rs.getString("job_id"),
            rs.getString("username"),
            rs.getString("reply_session_id"),
            RecognitionJobStatus.valueOf(rs.getString("status")),
            rs.getString("error_code"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()), jobId).stream().findFirst();
  }

  public int markRestartedTasksFailed(Instant now) {
    return jdbcTemplate.update("""
        UPDATE recognition_job_restart_recovery
        SET status = 'FAILED', error_code = ?, updated_at = ?
        WHERE status IN ('QUEUED', 'RECOGNIZING', 'READY')
        """, BACKEND_RESTARTED, Timestamp.from(now));
  }

  public void deleteTerminalBefore(Instant cutoff) {
    jdbcTemplate.update("""
        DELETE FROM recognition_job_restart_recovery
        WHERE status IN ('READY', 'FAILED', 'CANCELLED', 'EXPIRED') AND updated_at < ?
        """, Timestamp.from(cutoff));
  }

  public record RecoveredRecognitionJob(
      String jobId,
      String username,
      String replySessionId,
      RecognitionJobStatus status,
      String errorCode,
      Instant createdAt,
      Instant updatedAt) {

    RecognitionJobView view() {
      return new RecognitionJobView(
          jobId, replySessionId, status, errorCode, null, createdAt, updatedAt);
    }
  }
}
