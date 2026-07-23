package com.privateflow.modules.api.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.match.CustomerSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PendingReplyTaskRepository {

  private static final TypeReference<List<Map<String, String>>> CHAT_CONTEXT_TYPE = new TypeReference<>() {
  };
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public PendingReplyTaskRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public PendingReplyTask create(PendingReplyTaskDraft draft) {
    return create(draft, 24);
  }

  @Transactional
  public PendingReplyTask create(PendingReplyTaskDraft draft, int ttlHours) {
    LocalDateTime now = LocalDateTime.now();
    String taskId = UUID.randomUUID().toString();
    jdbcTemplate.update("""
        INSERT INTO pending_reply_tasks (
          task_id, reply_session_id, username, status, recognized_nickname, recognized_phone,
          platform_identifier, lead_type, source_table, client_message, chat_context_json,
          expires_at, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        taskId,
        draft.replySessionId(),
        draft.username(),
        PendingReplyTaskStatus.WAITING_CUSTOMER.name(),
        draft.recognizedNickname(),
        draft.recognizedPhone(),
        draft.platformIdentifier(),
        draft.leadType(),
        draft.sourceTable(),
        draft.clientMessage(),
        writeChatContext(draft.chatContext()),
        Timestamp.valueOf(now.plusHours(Math.max(1, ttlHours))),
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
    Long id = jdbcTemplate.queryForObject(
        "SELECT id FROM pending_reply_tasks WHERE task_id = ?",
        Long.class,
        taskId);
    if (id == null) {
      throw new IllegalStateException("pending reply task insert failed");
    }
    List<CustomerSummary> candidates = draft.candidates() == null ? List.of() : draft.candidates();
    for (int index = 0; index < candidates.size(); index++) {
      CustomerSummary candidate = candidates.get(index);
      String candidatePhone = candidate == null ? null : candidate.phoneFull();
      if (candidatePhone != null && !candidatePhone.isBlank()) {
        jdbcTemplate.update(
            "INSERT INTO pending_reply_task_candidates (task_id, phone, rank_no) VALUES (?, ?, ?)",
            id,
            candidatePhone,
            index + 1);
      }
    }
    return findById(id).orElseThrow(() -> new IllegalStateException("pending reply task read failed"));
  }

  public boolean claim(String taskId, String username, String phone) {
    return jdbcTemplate.update("""
        UPDATE pending_reply_tasks
        SET status = ?, selected_phone = ?, generation_started_at = CURRENT_TIMESTAMP,
            error_code = NULL, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE task_id = ? AND username = ?
          AND status = ?
          AND expires_at > CURRENT_TIMESTAMP
          AND EXISTS (
            SELECT 1
            FROM pending_reply_task_candidates candidate
            WHERE candidate.task_id = pending_reply_tasks.id
              AND candidate.phone = ?
          )
        """,
        PendingReplyTaskStatus.GENERATING.name(),
        phone,
        taskId,
        username,
        PendingReplyTaskStatus.WAITING_CUSTOMER.name(),
        phone) == 1;
  }

  public boolean claimRetry(String taskId, String username, String selectedPhone) {
    return jdbcTemplate.update("""
        UPDATE pending_reply_tasks
        SET status = ?, generation_started_at = CURRENT_TIMESTAMP,
            error_code = NULL, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE task_id = ? AND username = ? AND status = ?
          AND selected_phone = ?
          AND expires_at > CURRENT_TIMESTAMP
          AND EXISTS (
            SELECT 1
            FROM pending_reply_task_candidates candidate
            WHERE candidate.task_id = pending_reply_tasks.id
              AND candidate.phone = ?
          )
        """,
        PendingReplyTaskStatus.GENERATING.name(),
        taskId,
        username,
        PendingReplyTaskStatus.FAILED.name(),
        selectedPhone,
        selectedPhone) == 1;
  }

  public Optional<PendingReplyTask> findOwned(String taskId, String username) {
    List<PendingReplyTask> tasks = jdbcTemplate.query("""
        SELECT * FROM pending_reply_tasks WHERE task_id = ? AND username = ? LIMIT 1
        """, (resultSet, rowNumber) -> map(resultSet), taskId, username);
    return tasks.stream().findFirst();
  }

  public boolean markReady(String taskId, String username, ChatResponse response) {
    ReadyTaskTarget target = jdbcTemplate.query("""
        SELECT reply_session_id, selected_phone, version
        FROM pending_reply_tasks
        WHERE task_id = ? AND username = ? AND status = ?
        LIMIT 1
        """, (resultSet, rowNumber) -> new ReadyTaskTarget(
            resultSet.getString("reply_session_id"),
            resultSet.getString("selected_phone"),
            resultSet.getLong("version")),
        taskId,
        username,
        PendingReplyTaskStatus.GENERATING.name()).stream().findFirst().orElse(null);
    if (target == null || blank(target.replySessionId())) {
      return false;
    }
    if (!hasMatchingSelectedPhone(target.selectedPhone(), response)) {
      throw new IllegalArgumentException("invalid pending reply task response");
    }
    return jdbcTemplate.update("""
        UPDATE pending_reply_tasks
        SET status = ?, result_json = ?, error_code = NULL, finished_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE task_id = ? AND username = ? AND status = ?
          AND selected_phone = ? AND version = ?
        """,
        PendingReplyTaskStatus.READY.name(),
        writeResult(target.replySessionId(), response),
        taskId,
        username,
        PendingReplyTaskStatus.GENERATING.name(),
        target.selectedPhone(),
        target.version()) == 1;
  }

  public ChatResponse readResult(PendingReplyTask task) {
    if (task == null || blank(task.replySessionId()) || blank(task.resultJson())) {
      throw new IllegalStateException("invalid pending reply task result");
    }
    try {
      StoredReplyResult result = objectMapper.readValue(task.resultJson(), StoredReplyResult.class);
      if (result == null
          || blank(result.replySessionId())
          || !task.replySessionId().equals(result.replySessionId())
          || !hasDisplayableReply(result.response())
          || !hasMatchingSelectedPhone(task.selectedPhone(), result.response())) {
        throw new IllegalStateException("invalid pending reply task result");
      }
      return result.response();
    } catch (IllegalStateException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("invalid pending reply task result", ex);
    }
  }

  public boolean releaseSelection(String taskId, String username) {
    return jdbcTemplate.update("""
        UPDATE pending_reply_tasks
        SET status = ?, selected_phone = NULL, generation_started_at = NULL, finished_at = NULL,
            error_code = NULL, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE task_id = ? AND username = ? AND status = ?
        """,
        PendingReplyTaskStatus.WAITING_CUSTOMER.name(),
        taskId,
        username,
        PendingReplyTaskStatus.GENERATING.name()) == 1;
  }

  public boolean markFailed(String taskId, String username, String publicErrorCode) {
    return jdbcTemplate.update("""
        UPDATE pending_reply_tasks
        SET status = ?, error_code = ?, finished_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE task_id = ? AND username = ? AND status = ?
        """,
        PendingReplyTaskStatus.FAILED.name(),
        publicErrorCode,
        taskId,
        username,
        PendingReplyTaskStatus.GENERATING.name()) == 1;
  }

  public boolean cancel(String taskId, String username) {
    return jdbcTemplate.update("""
        UPDATE pending_reply_tasks
        SET status = ?, error_code = NULL, finished_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE task_id = ? AND username = ? AND status IN (?, ?)
        """,
        PendingReplyTaskStatus.CANCELLED.name(),
        taskId,
        username,
        PendingReplyTaskStatus.WAITING_CUSTOMER.name(),
        PendingReplyTaskStatus.FAILED.name()) == 1;
  }

  @Transactional
  public int recoverExpiredAndStalledTasks(LocalDateTime now, int generatingTimeoutSeconds) {
    return recoverExpiredAndStalledTasks(now, generatingTimeoutSeconds, Set.of());
  }

  @Transactional
  public int recoverExpiredAndStalledTasks(
      LocalDateTime now,
      int generatingTimeoutSeconds,
      Set<String> activeTaskIds) {
    if (now == null || generatingTimeoutSeconds <= 0) {
      throw new IllegalArgumentException("invalid pending reply task recovery parameters");
    }
    Timestamp timestamp = Timestamp.valueOf(now);
    int recovered = jdbcTemplate.update("""
        UPDATE pending_reply_tasks
        SET status = ?, error_code = NULL, finished_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE status IN (?, ?) AND expires_at <= ?
        """,
        PendingReplyTaskStatus.EXPIRED.name(),
        PendingReplyTaskStatus.WAITING_CUSTOMER.name(),
        PendingReplyTaskStatus.FAILED.name(),
        timestamp);
    List<String> activeIds = activeTaskIds == null
        ? List.of()
        : activeTaskIds.stream().filter(taskId -> !blank(taskId)).sorted().toList();
    String activeExclusion = activeIds.isEmpty()
        ? ""
        : " AND task_id NOT IN ("
            + String.join(", ", Collections.nCopies(activeIds.size(), "?"))
            + ")";
    String stalledSql = """
        UPDATE pending_reply_tasks
        SET status = ?, error_code = ?, finished_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE status = ? AND generation_started_at IS NOT NULL
          AND generation_started_at < ?
        """ + activeExclusion;
    List<Object> stalledParameters = new ArrayList<>();
    stalledParameters.add(PendingReplyTaskStatus.FAILED.name());
    stalledParameters.add(ApiErrorCodes.INTERNAL_ERROR);
    stalledParameters.add(PendingReplyTaskStatus.GENERATING.name());
    stalledParameters.add(Timestamp.valueOf(now.minusSeconds(generatingTimeoutSeconds)));
    stalledParameters.addAll(activeIds);
    recovered += jdbcTemplate.update(stalledSql, stalledParameters.toArray());
    return recovered;
  }

  @Transactional
  public int deletePhysicallyExpiredBefore(LocalDateTime cutoff) {
    if (cutoff == null) {
      throw new IllegalArgumentException("pending reply task cleanup cutoff is required");
    }
    return jdbcTemplate.update("""
        DELETE FROM pending_reply_tasks
        WHERE finished_at < ?
          AND status IN (?, ?, ?, ?)
        """,
        Timestamp.valueOf(cutoff),
        PendingReplyTaskStatus.EXPIRED.name(),
        PendingReplyTaskStatus.CANCELLED.name(),
        PendingReplyTaskStatus.READY.name(),
        PendingReplyTaskStatus.FAILED.name());
  }

  public List<PendingReplyTask> findActiveOwned(String username, LocalDateTime now) {
    if (blank(username) || now == null) {
      return List.of();
    }
    return jdbcTemplate.query("""
        SELECT * FROM pending_reply_tasks
        WHERE username = ? AND expires_at > ?
          AND status IN (?, ?, ?, ?)
        ORDER BY created_at ASC, id ASC
        """,
        (resultSet, rowNumber) -> map(resultSet),
        username,
        Timestamp.valueOf(now),
        PendingReplyTaskStatus.WAITING_CUSTOMER.name(),
        PendingReplyTaskStatus.GENERATING.name(),
        PendingReplyTaskStatus.FAILED.name(),
        PendingReplyTaskStatus.READY.name());
  }

  private Optional<PendingReplyTask> findById(long id) {
    List<PendingReplyTask> tasks = jdbcTemplate.query(
        "SELECT * FROM pending_reply_tasks WHERE id = ? LIMIT 1",
        (resultSet, rowNumber) -> map(resultSet),
        id);
    return tasks.stream().findFirst();
  }

  private PendingReplyTask map(ResultSet resultSet) throws SQLException {
    long id = resultSet.getLong("id");
    return new PendingReplyTask(
        id,
        resultSet.getString("task_id"),
        resultSet.getString("reply_session_id"),
        resultSet.getString("username"),
        PendingReplyTaskStatus.valueOf(resultSet.getString("status")),
        resultSet.getString("recognized_nickname"),
        resultSet.getString("recognized_phone"),
        resultSet.getString("platform_identifier"),
        resultSet.getString("lead_type"),
        resultSet.getString("source_table"),
        resultSet.getString("client_message"),
        readChatContext(resultSet.getString("chat_context_json")),
        candidatePhones(id),
        resultSet.getString("selected_phone"),
        resultSet.getString("result_json"),
        resultSet.getString("error_code"),
        time(resultSet.getTimestamp("generation_started_at")),
        time(resultSet.getTimestamp("expires_at")),
        time(resultSet.getTimestamp("created_at")),
        time(resultSet.getTimestamp("updated_at")));
  }

  private List<String> candidatePhones(long taskId) {
    return jdbcTemplate.query(
        "SELECT phone FROM pending_reply_task_candidates WHERE task_id = ? ORDER BY rank_no",
        (resultSet, rowNumber) -> resultSet.getString("phone"),
        taskId);
  }

  private String writeChatContext(List<Map<String, String>> chatContext) {
    try {
      return objectMapper.writeValueAsString(chatContext == null ? List.of() : chatContext);
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid chat context", ex);
    }
  }

  private String writeResult(String replySessionId, ChatResponse response) {
    if (blank(replySessionId) || !hasDisplayableReply(response)) {
      throw new IllegalArgumentException("invalid pending reply task response");
    }
    try {
      return objectMapper.writeValueAsString(new StoredReplyResult(replySessionId, response));
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid pending reply task response", ex);
    }
  }

  private boolean hasDisplayableReply(ChatResponse response) {
    return response != null
        && response.skill() != null
        && response.skill().suggestions() != null
        && response.skill().suggestions().stream()
            .anyMatch(suggestion -> suggestion != null && !blank(suggestion.text()));
  }

  private boolean hasMatchingSelectedPhone(String selectedPhone, ChatResponse response) {
    return !blank(selectedPhone)
        && response != null
        && !blank(response.phone())
        && selectedPhone.equals(response.phone());
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private List<Map<String, String>> readChatContext(String raw) {
    try {
      return raw == null || raw.isBlank() ? List.of() : objectMapper.readValue(raw, CHAT_CONTEXT_TYPE);
    } catch (Exception ex) {
      throw new IllegalStateException("invalid pending reply task context", ex);
    }
  }

  private LocalDateTime time(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }

  private record StoredReplyResult(String replySessionId, ChatResponse response) {
  }

  private record ReadyTaskTarget(String replySessionId, String selectedPhone, long version) {
  }
}
