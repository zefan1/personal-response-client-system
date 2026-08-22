package com.privateflow.modules.profile.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.CustomerMessageSentEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ProfileUpdateFailureRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public ProfileUpdateFailureRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public long recordFailure(
      long customerId,
      String phone,
      List<CustomerMessageSentEvent.ChatMessage> rawMessages,
      String operator,
      String stage,
      Throwable error) {
    String messages;
    try {
      messages = objectMapper.writeValueAsString(rawMessages == null ? List.of() : rawMessages);
    } catch (Exception ex) {
      messages = "[]";
    }
    String persistedMessages = messages;
    String errorMessage = error == null ? "unknown profile update failure" : error.getMessage();
    if (errorMessage != null && errorMessage.length() > 1000) {
      errorMessage = errorMessage.substring(0, 1000);
    }
    String persistedErrorMessage = errorMessage;
    String persistedErrorCode = error == null ? null : error.getClass().getSimpleName();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      var statement = connection.prepareStatement("""
          INSERT INTO profile_update_failures
            (customer_id, phone, raw_messages_json, operator, stage, error_code, error_message,
             status, retry_count, last_attempt_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, 'FAILED', 0, NOW(), NOW())
          """, Statement.RETURN_GENERATED_KEYS);
      statement.setLong(1, customerId);
      statement.setString(2, phone);
      statement.setString(3, persistedMessages);
      statement.setString(4, operator);
      statement.setString(5, stage);
      statement.setString(6, persistedErrorCode);
      statement.setString(7, persistedErrorMessage);
      return statement;
    }, keyHolder);
    java.util.Map<String, Object> keys = keyHolder.getKeys();
    Number id = keys == null ? keyHolder.getKey() : keys.values().stream()
        .filter(Number.class::isInstance)
        .map(Number.class::cast)
        .findFirst()
        .orElseGet(keyHolder::getKey);
    if (id == null) {
      throw new IllegalStateException("profile update failure id was not generated");
    }
    return id.longValue();
  }

  public List<ProfileUpdateFailureRecord> list(int limit) {
    int actualLimit = Math.max(1, Math.min(limit, 200));
    return jdbcTemplate.query("""
        SELECT id, customer_id, phone, raw_messages_json, operator, stage, error_code,
               error_message, status, retry_count, created_at, updated_at
        FROM profile_update_failures
        ORDER BY updated_at DESC, id DESC
        LIMIT ?
        """, this::map, actualLimit);
  }

  public Optional<ProfileUpdateFailureRecord> find(long id) {
    return jdbcTemplate.query("""
        SELECT id, customer_id, phone, raw_messages_json, operator, stage, error_code,
               error_message, status, retry_count, created_at, updated_at
        FROM profile_update_failures WHERE id = ? LIMIT 1
        """, this::map, id).stream().findFirst();
  }

  public boolean markRetrying(long id) {
    return jdbcTemplate.update("""
        UPDATE profile_update_failures
        SET status = 'RETRYING', retry_count = retry_count + 1,
            last_attempt_at = NOW(), updated_at = NOW()
        WHERE id = ? AND status IN ('FAILED', 'RETRYING')
        """, id) == 1;
  }

  public void markSucceeded(long id) {
    jdbcTemplate.update("UPDATE profile_update_failures SET status = 'SUCCEEDED', updated_at = NOW() WHERE id = ?", id);
  }

  public void markFailed(long id, String stage, Throwable error) {
    String message = error == null ? "unknown profile update failure" : error.getMessage();
    if (message != null && message.length() > 1000) {
      message = message.substring(0, 1000);
    }
    jdbcTemplate.update("""
        UPDATE profile_update_failures
        SET status = 'FAILED', stage = ?, error_code = ?, error_message = ?, updated_at = NOW()
        WHERE id = ?
        """, stage, error == null ? null : error.getClass().getSimpleName(), message, id);
  }

  private ProfileUpdateFailureRecord map(ResultSet rs, int rowNum) throws SQLException {
    List<CustomerMessageSentEvent.ChatMessage> messages;
    try {
      messages = objectMapper.readValue(rs.getString("raw_messages_json"),
          new TypeReference<List<CustomerMessageSentEvent.ChatMessage>>() {});
    } catch (Exception ex) {
      messages = List.of();
    }
    return new ProfileUpdateFailureRecord(
        rs.getLong("id"), rs.getLong("customer_id"), rs.getString("phone"), messages,
        rs.getString("operator"), rs.getString("stage"), rs.getString("error_code"),
        rs.getString("error_message"), rs.getString("status"), rs.getInt("retry_count"),
        timestamp(rs, "created_at"), timestamp(rs, "updated_at"));
  }

  private LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
    return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
  }
}
