package com.privateflow.modules.supervision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

  public SupervisionEventPage findPage(SupervisionEventQuery query) {
    List<Object> arguments = new ArrayList<>();
    String filters = eventFilters(query, arguments);
    Long total = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM supervision_events event WHERE " + filters,
        Long.class,
        arguments.toArray());

    List<Object> pageArguments = new ArrayList<>(arguments);
    pageArguments.add(query.pageSize());
    pageArguments.add((query.page() - 1L) * query.pageSize());
    List<SupervisionEventView> items = jdbcTemplate.query("""
        SELECT event.id,
               event.event_type,
               event.operator_username,
               event.customer_phone,
               event.channel_code,
               event.lead_source,
               event.assigned_keeper,
               event.scene,
               event.reply_source,
               event.generated_reply_snapshot,
               event.copied_reply_snapshot,
               event.occurred_at
        FROM supervision_events event
        WHERE %s
        ORDER BY event.occurred_at DESC, event.id DESC
        LIMIT ? OFFSET ?
        """.formatted(filters),
        (rs, rowNum) -> new SupervisionEventView(
            rs.getLong("id"),
            SupervisionEventType.valueOf(rs.getString("event_type")),
            rs.getString("operator_username"),
            maskPhone(rs.getString("customer_phone")),
            rs.getString("channel_code"),
            rs.getString("lead_source"),
            rs.getString("assigned_keeper"),
            rs.getString("scene"),
            rs.getString("reply_source"),
            replyPreview(rs.getString("copied_reply_snapshot"), rs.getString("generated_reply_snapshot")),
            rs.getTimestamp("occurred_at").toLocalDateTime()),
        pageArguments.toArray());
    return new SupervisionEventPage(items, total == null ? 0L : total, query.page(), query.pageSize());
  }

  public SupervisionMetadata metadata() {
    return new SupervisionMetadata(
        dimensionValues("operator_username", "assigned_keeper"),
        dimensionValues("channel_code", "source_channel"),
        dimensionValues("lead_source", "source_table"),
        jdbcTemplate.queryForList("""
            SELECT DISTINCT customer_stage
            FROM customers
            WHERE customer_stage IS NOT NULL AND customer_stage <> ''
            ORDER BY customer_stage
            """, String.class),
        jdbcTemplate.queryForList("""
            SELECT DISTINCT event_type
            FROM supervision_events
            WHERE event_type IS NOT NULL AND event_type <> ''
            ORDER BY event_type
            """, String.class));
  }

  private List<String> dimensionValues(String eventColumn, String customerColumn) {
    return jdbcTemplate.queryForList("""
        SELECT dimension_value FROM (
          SELECT DISTINCT %s AS dimension_value
          FROM supervision_events
          WHERE %s IS NOT NULL AND %s <> ''
          UNION
          SELECT DISTINCT %s AS dimension_value
          FROM customers
          WHERE %s IS NOT NULL AND %s <> ''
        ) values_table
        ORDER BY dimension_value
        """.formatted(
        eventColumn,
        eventColumn,
        eventColumn,
        customerColumn,
        customerColumn,
        customerColumn), String.class);
  }

  private String eventFilters(SupervisionEventQuery query, List<Object> arguments) {
    List<String> conditions = new ArrayList<>();
    conditions.add("event.occurred_at >= ?");
    arguments.add(Timestamp.valueOf(query.fromInclusive()));
    conditions.add("event.occurred_at < ?");
    arguments.add(Timestamp.valueOf(query.toExclusive()));
    addOptionalFilter(conditions, arguments, "event.operator_username", query.operatorUsername());
    addOptionalFilter(conditions, arguments, "event.channel_code", query.channelCode());
    addOptionalFilter(conditions, arguments, "event.lead_source", query.leadSource());
    if (query.eventType() != null) {
      conditions.add("event.event_type = ?");
      arguments.add(query.eventType().name());
    }
    return String.join(" AND ", conditions);
  }

  private void addOptionalFilter(
      List<String> conditions,
      List<Object> arguments,
      String field,
      String value) {
    if (value != null) {
      conditions.add(field + " = ?");
      arguments.add(value);
    }
  }

  private String maskPhone(String phone) {
    if (phone == null || phone.isBlank()) {
      return null;
    }
    String value = phone.trim();
    if (value.length() <= 7) {
      return "****";
    }
    return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
  }

  private String replyPreview(String copiedReply, String generatedReply) {
    String reply = copiedReply == null || copiedReply.isBlank() ? generatedReply : copiedReply;
    if (reply == null || reply.isBlank()) {
      return null;
    }
    String masked = reply.replaceAll("(?<!\\d)(\\d{3})[\\s-]?\\d{4}[\\s-]?(\\d{4})(?!\\d)", "$1****$2")
        .replaceAll("\\s+", " ")
        .trim();
    return masked.length() <= 500 ? masked : masked.substring(0, 500) + "...";
  }

  private String metadataJson(SupervisionEventCommand event) {
    try {
      return objectMapper.writeValueAsString(event.metadata());
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("cannot serialize supervision event metadata", ex);
    }
  }
}
