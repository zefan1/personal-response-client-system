package com.privateflow.modules.communication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CommunicationArchiveRepository {

  private final JdbcTemplate jdbcTemplate;

  public CommunicationArchiveRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public ArchivedCommunicationBatch archive(CommunicationBatchDraft draft) {
    validate(draft);
    LocalDateTime now = draft.recognizedAt();
    String batchId = UUID.randomUUID().toString();
    if (draft.customerId() == null || draft.customerId() <= 0) {
      throw new IllegalArgumentException("communication batch customer id is required");
    }
    jdbcTemplate.update("""
        INSERT INTO communication_recognition_batches (
          batch_id, username, platform_code, platform_identifier, recognized_nickname,
          recognized_phone, customer_id, raw_text, recognized_at, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        batchId,
        draft.username().trim(),
        normalizeCode(draft.platformCode()),
        trimToNull(draft.platformIdentifier()),
        trimToNull(draft.recognizedNickname()),
        trimToNull(draft.recognizedPhone()),
        draft.customerId(),
        draft.rawText(),
        timestamp(now),
        timestamp(now),
        timestamp(now));
    Long id = jdbcTemplate.queryForObject(
        "SELECT id FROM communication_recognition_batches WHERE batch_id = ?",
        Long.class,
        batchId);
    if (id == null) {
      throw new IllegalStateException("communication batch insert failed");
    }
    List<CommunicationMessageDraft> messages = draft.messages() == null ? List.of() : draft.messages();
    for (int index = 0; index < messages.size(); index++) {
      insertMessage(id, draft, messages.get(index), index + 1, now);
    }
    return findBatch(id).orElseThrow(() -> new IllegalStateException("communication batch read failed"));
  }

  public Optional<ArchivedCommunicationBatch> findBatch(long id) {
    return jdbcTemplate.query(
        "SELECT * FROM communication_recognition_batches WHERE id = ?",
        (resultSet, rowNumber) -> mapBatch(resultSet),
        id).stream().findFirst();
  }

  public List<ArchivedCommunicationMessage> findMessagesForCustomer(long customerId) {
    return jdbcTemplate.query("""
        SELECT * FROM communication_messages
        WHERE customer_id = ?
        ORDER BY message_time ASC, id ASC
        """, (resultSet, rowNumber) -> mapMessage(resultSet), customerId);
  }

  public List<ArchivedCommunicationMessage> findMessagesAfter(
      long customerId,
      long lastMessageId,
      int limit) {
    if (limit <= 0) {
      return List.of();
    }
    return jdbcTemplate.query("""
        SELECT * FROM communication_messages
        WHERE customer_id = ? AND id > ?
        ORDER BY id ASC LIMIT ?
        """, (resultSet, rowNumber) -> mapMessage(resultSet), customerId, lastMessageId, limit);
  }

  public List<ArchivedCommunicationMessage> searchCustomerMessages(
      long customerId,
      String platformCode,
      LocalDateTime from,
      LocalDateTime toExclusive,
      String keyword,
      Long beforeId,
      int limit) {
    StringBuilder sql = new StringBuilder(
        "SELECT * FROM communication_messages WHERE customer_id = ?");
    List<Object> parameters = new ArrayList<>();
    parameters.add(customerId);
    if (!blank(platformCode)) {
      sql.append(" AND platform_code = ?");
      parameters.add(normalizeCode(platformCode));
    }
    if (from != null) {
      sql.append(" AND message_time >= ?");
      parameters.add(timestamp(from));
    }
    if (toExclusive != null) {
      sql.append(" AND message_time < ?");
      parameters.add(timestamp(toExclusive));
    }
    if (!blank(keyword)) {
      sql.append(" AND current_text LIKE ?");
      parameters.add("%" + keyword.trim() + "%");
    }
    if (beforeId != null) {
      sql.append(" AND id < ?");
      parameters.add(beforeId);
    }
    sql.append(" ORDER BY id DESC LIMIT ?");
    parameters.add(limit);
    return jdbcTemplate.query(
        sql.toString(),
        (resultSet, rowNumber) -> mapMessage(resultSet),
        parameters.toArray());
  }

  public Optional<ArchivedCommunicationMessage> findMessage(long messageId) {
    return jdbcTemplate.query(
        "SELECT * FROM communication_messages WHERE id = ?",
        (resultSet, rowNumber) -> mapMessage(resultSet),
        messageId).stream().findFirst();
  }

  public Optional<String> findCustomerPhone(long customerId) {
    return jdbcTemplate.query(
        "SELECT phone FROM customers WHERE id = ?",
        (resultSet, rowNumber) -> resultSet.getString("phone"),
        customerId).stream().findFirst();
  }

  public List<CommunicationMessageDraft> findRecentMessageDrafts(
      CommunicationBatchDraft scope,
      int limit) {
    if (scope == null || limit <= 0) {
      return List.of();
    }
    List<CommunicationMessageDraft> descending;
    if (scope.customerId() == null || scope.customerId() <= 0) {
      return List.of();
    }
    descending = jdbcTemplate.query("""
        SELECT sender_role, original_text, content_type, message_time, time_estimated
        FROM communication_messages
        WHERE customer_id = ? AND platform_code = ?
        ORDER BY message_time DESC, id DESC LIMIT ?
        """, (resultSet, rowNumber) -> mapDraft(resultSet),
        scope.customerId(), normalizeCode(scope.platformCode()), limit);
    List<CommunicationMessageDraft> chronological = new ArrayList<>(descending);
    Collections.reverse(chronological);
    return List.copyOf(chronological);
  }

  @Transactional
  public void correctMessage(
      long messageId,
      String correctedText,
      String correctedBy,
      LocalDateTime correctedAt) {
    if (blank(correctedText) || blank(correctedBy) || correctedAt == null) {
      throw new IllegalArgumentException("valid correction is required");
    }
    String previousText = jdbcTemplate.query(
        "SELECT current_text FROM communication_messages WHERE id = ?",
        (resultSet, rowNumber) -> resultSet.getString("current_text"),
        messageId).stream().findFirst()
        .orElseThrow(() -> new IllegalArgumentException("communication message not found"));
    jdbcTemplate.update("""
        INSERT INTO communication_message_corrections (
          message_id, previous_text, corrected_text, corrected_by, corrected_at
        ) VALUES (?, ?, ?, ?, ?)
        """, messageId, previousText, correctedText.trim(), correctedBy.trim(), timestamp(correctedAt));
    jdbcTemplate.update(
        "UPDATE communication_messages SET current_text = ?, updated_at = ? WHERE id = ?",
        correctedText.trim(),
        timestamp(correctedAt),
        messageId);
  }

  public List<CommunicationMessageCorrection> findCorrections(long messageId) {
    return jdbcTemplate.query("""
        SELECT * FROM communication_message_corrections
        WHERE message_id = ? ORDER BY corrected_at ASC, id ASC
        """, (resultSet, rowNumber) -> new CommunicationMessageCorrection(
            resultSet.getLong("id"),
            resultSet.getLong("message_id"),
            resultSet.getString("previous_text"),
            resultSet.getString("corrected_text"),
            resultSet.getString("corrected_by"),
            time(resultSet.getTimestamp("corrected_at"))), messageId);
  }

  public void markSummaryPending(long customerId, LocalDateTime now) {
    upsertSummaryState(customerId, "PENDING", null, 0, null, null, now);
  }

  public void markSummaryFailed(
      long customerId,
      int retryCount,
      LocalDateTime nextRetryAt,
      String error) {
    upsertSummaryState(
        customerId, "RETRY_PENDING", null, retryCount, nextRetryAt, clip(error, 500),
        LocalDateTime.now());
  }

  @Transactional
  public CommunicationSummaryVersion appendSummaryVersion(
      long customerId,
      String summaryText,
      long lastMessageId,
      LocalDateTime generatedAt) {
    if (blank(summaryText) || generatedAt == null) {
      throw new IllegalArgumentException("valid communication summary is required");
    }
    Integer version = jdbcTemplate.queryForObject("""
        SELECT COALESCE(MAX(version_no), 0) + 1
        FROM communication_summary_versions WHERE customer_id = ?
        """, Integer.class, customerId);
    int versionNo = version == null ? 1 : version;
    jdbcTemplate.update("""
        INSERT INTO communication_summary_versions (
          customer_id, version_no, summary_text, last_message_id, generated_at, created_at
        ) VALUES (?, ?, ?, ?, ?, ?)
        """, customerId, versionNo, summaryText.trim(), lastMessageId,
        timestamp(generatedAt), timestamp(generatedAt));
    upsertSummaryState(
        customerId, "CURRENT", lastMessageId, 0, null, null, generatedAt);
    return findSummaryVersions(customerId).stream()
        .filter(item -> item.versionNo() == versionNo)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("communication summary insert failed"));
  }

  public List<CommunicationSummaryVersion> findSummaryVersions(long customerId) {
    return jdbcTemplate.query("""
        SELECT * FROM communication_summary_versions
        WHERE customer_id = ? ORDER BY version_no DESC
        """, (resultSet, rowNumber) -> new CommunicationSummaryVersion(
            resultSet.getLong("id"),
            resultSet.getLong("customer_id"),
            resultSet.getInt("version_no"),
            resultSet.getString("summary_text"),
            resultSet.getLong("last_message_id"),
            time(resultSet.getTimestamp("generated_at"))), customerId);
  }

  public Optional<CommunicationSummaryState> findSummaryState(long customerId) {
    return jdbcTemplate.query(
        "SELECT * FROM communication_summary_states WHERE customer_id = ?",
        (resultSet, rowNumber) -> new CommunicationSummaryState(
            resultSet.getLong("customer_id"),
            resultSet.getString("status"),
            nullableLong(resultSet, "last_summarized_message_id"),
            resultSet.getInt("retry_count"),
            time(resultSet.getTimestamp("next_retry_at")),
            resultSet.getString("last_error"),
            time(resultSet.getTimestamp("updated_at"))),
        customerId).stream().findFirst();
  }

  public List<CommunicationSummaryState> findSummaryStatesDue(LocalDateTime now, int limit) {
    if (now == null || limit <= 0) {
      return List.of();
    }
    return jdbcTemplate.query("""
        SELECT * FROM communication_summary_states
        WHERE status = 'PENDING'
           OR (status = 'RETRY_PENDING' AND next_retry_at <= ?)
        ORDER BY updated_at ASC LIMIT ?
        """, (resultSet, rowNumber) -> new CommunicationSummaryState(
            resultSet.getLong("customer_id"),
            resultSet.getString("status"),
            nullableLong(resultSet, "last_summarized_message_id"),
            resultSet.getInt("retry_count"),
            time(resultSet.getTimestamp("next_retry_at")),
            resultSet.getString("last_error"),
            time(resultSet.getTimestamp("updated_at"))),
        timestamp(now),
        limit);
  }

  private void insertMessage(
      long batchId,
      CommunicationBatchDraft batch,
      CommunicationMessageDraft message,
      int sequenceNo,
      LocalDateTime now) {
    if (message == null || blank(message.senderRole()) || blank(message.text())) {
      throw new IllegalArgumentException("valid communication message is required");
    }
    LocalDateTime messageTime = message.messageTime() == null ? batch.recognizedAt() : message.messageTime();
    boolean estimated = message.messageTime() == null || message.timeEstimated();
    String role = normalizeCode(message.senderRole());
    String text = message.text().trim();
    String contentType = blank(message.contentType()) ? "TEXT" : normalizeCode(message.contentType());
    jdbcTemplate.update("""
        INSERT INTO communication_messages (
          batch_id, customer_id, username, platform_code, sender_role, content_type,
          original_text, current_text, message_time, time_estimated, sequence_no,
          dedupe_fingerprint, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        batchId,
        batch.customerId(),
        batch.username().trim(),
        normalizeCode(batch.platformCode()),
        role,
        contentType,
        text,
        text,
        timestamp(messageTime),
        estimated ? 1 : 0,
        sequenceNo,
        fingerprint(role, text, contentType),
        timestamp(now),
        timestamp(now));
  }

  private void upsertSummaryState(
      long customerId,
      String status,
      Long lastMessageId,
      int retryCount,
      LocalDateTime nextRetryAt,
      String error,
      LocalDateTime updatedAt) {
    LocalDateTime now = updatedAt == null ? LocalDateTime.now() : updatedAt;
    int updated = jdbcTemplate.update("""
        UPDATE communication_summary_states
        SET status = ?, last_summarized_message_id = COALESCE(?, last_summarized_message_id),
            retry_count = ?, next_retry_at = ?, last_error = ?, updated_at = ?
        WHERE customer_id = ?
        """, status, lastMessageId, retryCount, timestamp(nextRetryAt), error, timestamp(now), customerId);
    if (updated == 0) {
      jdbcTemplate.update("""
          INSERT INTO communication_summary_states (
            customer_id, status, last_summarized_message_id, retry_count,
            next_retry_at, last_error, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?)
          """, customerId, status, lastMessageId, retryCount,
          timestamp(nextRetryAt), error, timestamp(now));
    }
  }

  private ArchivedCommunicationBatch mapBatch(ResultSet resultSet) throws SQLException {
    return new ArchivedCommunicationBatch(
        resultSet.getLong("id"),
        resultSet.getString("batch_id"),
        resultSet.getString("username"),
        resultSet.getString("platform_code"),
        resultSet.getString("platform_identifier"),
        resultSet.getString("recognized_nickname"),
        resultSet.getString("recognized_phone"),
        nullableLong(resultSet, "customer_id"),
        resultSet.getString("raw_text"),
        time(resultSet.getTimestamp("recognized_at")));
  }

  private ArchivedCommunicationMessage mapMessage(ResultSet resultSet) throws SQLException {
    return new ArchivedCommunicationMessage(
        resultSet.getLong("id"),
        resultSet.getLong("batch_id"),
        nullableLong(resultSet, "customer_id"),
        resultSet.getString("username"),
        resultSet.getString("platform_code"),
        resultSet.getString("sender_role"),
        resultSet.getString("content_type"),
        resultSet.getString("original_text"),
        resultSet.getString("current_text"),
        time(resultSet.getTimestamp("message_time")),
        resultSet.getInt("time_estimated") == 1,
        resultSet.getInt("sequence_no"),
        resultSet.getString("dedupe_fingerprint"));
  }

  private CommunicationMessageDraft mapDraft(ResultSet resultSet) throws SQLException {
    return new CommunicationMessageDraft(
        resultSet.getString("sender_role"),
        resultSet.getString("original_text"),
        resultSet.getString("content_type"),
        time(resultSet.getTimestamp("message_time")),
        resultSet.getInt("time_estimated") == 1);
  }

  private void validate(CommunicationBatchDraft draft) {
    if (draft == null || blank(draft.username()) || blank(draft.platformCode())
        || draft.rawText() == null || draft.recognizedAt() == null) {
      throw new IllegalArgumentException("valid communication batch is required");
    }
  }

  private String fingerprint(String role, String text, String contentType) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(
          (role + "\u0000" + normalizeText(text) + "\u0000" + contentType)
              .getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
    long value = resultSet.getLong(column);
    return resultSet.wasNull() ? null : value;
  }

  private Timestamp timestamp(LocalDateTime value) {
    return value == null ? null : Timestamp.valueOf(value);
  }

  private LocalDateTime time(Timestamp value) {
    return value == null ? null : value.toLocalDateTime();
  }

  private String normalizeCode(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  private String normalizeText(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
  }

  private String normalizeIdentifier(String value) {
    return value == null
        ? ""
        : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private String trimToNull(String value) {
    return blank(value) ? null : value.trim();
  }

  private String clip(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
