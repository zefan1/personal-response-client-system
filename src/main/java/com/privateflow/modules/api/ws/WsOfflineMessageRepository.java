package com.privateflow.modules.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WsOfflineMessageRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final AtomicLong fallbackId = new AtomicLong(System.currentTimeMillis());

  public WsOfflineMessageRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public long nextMessageId() {
    try {
      Long next = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(message_id), 0) + 1 FROM ws_offline_queue", Long.class);
      return next == null ? fallbackId.incrementAndGet() : Math.max(next, fallbackId.incrementAndGet());
    } catch (RuntimeException ex) {
      return fallbackId.incrementAndGet();
    }
  }

  public void save(String username, WsMessage message) {
    try {
      jdbcTemplate.update("""
          INSERT IGNORE INTO ws_offline_queue (message_id, username, message_type, payload, delivered)
          VALUES (?, ?, ?, ?, 0)
          """, message.messageId(), username, message.type(), objectMapper.writeValueAsString(message.payload()));
    } catch (Exception ex) {
      throw new IllegalStateException("failed to save offline WS message", ex);
    }
  }

  public List<WsMessage> replay(String username, long afterMessageId, int limit) {
    return jdbcTemplate.query("""
        SELECT message_id, message_type, payload
        FROM ws_offline_queue
        WHERE username = ? AND message_id > ?
        ORDER BY message_id ASC
        LIMIT ?
        """, (rs, rowNum) -> map(rs), username, afterMessageId, limit);
  }

  public void markDelivered(String username, long messageId) {
    jdbcTemplate.update("""
        UPDATE ws_offline_queue
        SET delivered = 1, delivered_at = NOW()
        WHERE username = ? AND message_id = ?
        """, username, messageId);
  }

  public void cleanup(int retentionDays) {
    jdbcTemplate.update("""
        DELETE FROM ws_offline_queue
        WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)
        """, retentionDays);
  }

  private WsMessage map(ResultSet rs) throws SQLException {
    try {
      Object payload = objectMapper.readValue(rs.getString("payload"), Object.class);
      return new WsMessage(rs.getLong("message_id"), rs.getString("message_type"), payload);
    } catch (Exception ex) {
      return new WsMessage(rs.getLong("message_id"), rs.getString("message_type"), rs.getString("payload"));
    }
  }
}
