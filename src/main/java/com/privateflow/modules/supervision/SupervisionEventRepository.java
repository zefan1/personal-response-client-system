package com.privateflow.modules.supervision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SupervisionEventRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public SupervisionEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public void insert(SupervisionEventCommand event) {
    jdbcTemplate.update("""
        INSERT INTO supervision_events (
          event_id,
          event_type,
          operator_username,
          customer_phone,
          channel_code,
          channel_account,
          lead_source,
          assigned_keeper,
          scene,
          task_id,
          reply_session_id,
          reply_source,
          dedupe_key,
          generated_reply_snapshot,
          copied_reply_snapshot,
          metadata_json,
          occurred_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        event.eventId(),
        event.eventType().name(),
        event.operatorUsername(),
        event.customerPhone(),
        event.channelCode(),
        event.channelAccount(),
        event.leadSource(),
        event.assignedKeeper(),
        event.scene(),
        event.taskId(),
        event.replySessionId(),
        event.replySource(),
        event.dedupeKey(),
        event.generatedReplySnapshot(),
        event.copiedReplySnapshot(),
        metadataJson(event),
        event.occurredAt());
  }

  public int deleteEventsBefore(LocalDateTime cutoff) {
    if (cutoff == null) {
      throw new IllegalArgumentException("supervision event cleanup cutoff is required");
    }
    return jdbcTemplate.update(
        "DELETE FROM supervision_events WHERE occurred_at < ?",
        Timestamp.valueOf(cutoff));
  }

  private String metadataJson(SupervisionEventCommand event) {
    try {
      return objectMapper.writeValueAsString(event.metadata());
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("cannot serialize supervision event metadata", ex);
    }
  }
}
