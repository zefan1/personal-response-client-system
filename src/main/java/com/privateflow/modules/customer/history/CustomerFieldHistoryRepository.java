package com.privateflow.modules.customer.history;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerFieldHistoryRepository {

  private final JdbcTemplate jdbcTemplate;

  public CustomerFieldHistoryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void append(
      long customerId,
      String fieldName,
      String value,
      CustomerFieldHistoryContext context) {
    jdbcTemplate.update("""
        INSERT INTO customer_field_history
          (customer_id, field_name, field_value, source, source_field, operator)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        customerId,
        fieldName,
        value,
        context.source(),
        context.sourceField(fieldName),
        context.operator());
  }

  public List<CustomerFieldHistoryEntry> list(long customerId, String fieldName) {
    return jdbcTemplate.query("""
        SELECT id, field_name, field_value, source, source_field, operator, changed_at
        FROM customer_field_history
        WHERE customer_id = ? AND field_name = ?
        ORDER BY changed_at ASC, id ASC
        """, (rs, rowNum) -> new CustomerFieldHistoryEntry(
        rs.getLong("id"),
        rs.getString("field_name"),
        rs.getString("field_value"),
        rs.getString("source"),
        rs.getString("source_field"),
        rs.getString("operator"),
        timestamp(rs.getTimestamp("changed_at"))), customerId, fieldName);
  }

  public CustomerFieldHistoryEntry latest(long customerId, String fieldName) {
    return jdbcTemplate.query("""
        SELECT id, field_name, field_value, source, source_field, operator, changed_at
        FROM customer_field_history
        WHERE customer_id = ? AND field_name = ?
        ORDER BY changed_at DESC, id DESC
        LIMIT 1
        """, (rs, rowNum) -> new CustomerFieldHistoryEntry(
        rs.getLong("id"),
        rs.getString("field_name"),
        rs.getString("field_value"),
        rs.getString("source"),
        rs.getString("source_field"),
        rs.getString("operator"),
        timestamp(rs.getTimestamp("changed_at"))), customerId, fieldName)
        .stream().findFirst().orElse(null);
  }

  public Map<String, CustomerFieldHistoryEntry> latestByCustomer(long customerId) {
    Map<String, CustomerFieldHistoryEntry> latest = new LinkedHashMap<>();
    jdbcTemplate.query("""
        SELECT id, field_name, field_value, source, source_field, operator, changed_at
        FROM customer_field_history
        WHERE customer_id = ?
        ORDER BY changed_at DESC, id DESC
        """, (rs, rowNum) -> new CustomerFieldHistoryEntry(
        rs.getLong("id"),
        rs.getString("field_name"),
        rs.getString("field_value"),
        rs.getString("source"),
        rs.getString("source_field"),
        rs.getString("operator"),
        timestamp(rs.getTimestamp("changed_at"))), customerId)
        .forEach(entry -> latest.putIfAbsent(entry.fieldName(), entry));
    return latest;
  }

  private LocalDateTime timestamp(Timestamp value) {
    return value == null ? null : value.toLocalDateTime();
  }
}
