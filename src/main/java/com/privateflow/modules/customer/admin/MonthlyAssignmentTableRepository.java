package com.privateflow.modules.customer.admin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MonthlyAssignmentTableRepository {

  private final JdbcTemplate jdbcTemplate;

  public MonthlyAssignmentTableRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public long createPending(String tableName, String monthKey, String createdBy) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    try {
      jdbcTemplate.update(connection -> {
        PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO monthly_assignment_tables
                (table_name, month_key, document_id, sheet_id, view_id, document_url, status, created_by)
            VALUES (?, ?, '', '', '', '', 'CREATING', ?)
            """, new String[] {"id"});
        statement.setString(1, tableName);
        statement.setString(2, monthKey);
        statement.setString(3, createdBy);
        return statement;
      }, keyHolder);
    } catch (DuplicateKeyException ex) {
      throw ex;
    }
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("创建分配表记录后未返回编号");
    }
    return key.longValue();
  }

  public Optional<MonthlyAssignmentTable> findByName(String tableName) {
    return query("SELECT * FROM monthly_assignment_tables WHERE table_name = ? LIMIT 1", tableName)
        .stream().findFirst();
  }

  public Optional<MonthlyAssignmentTable> findById(long id) {
    return query("SELECT * FROM monthly_assignment_tables WHERE id = ? LIMIT 1", id).stream().findFirst();
  }

  public List<MonthlyAssignmentTable> list(int limit) {
    int safeLimit = Math.max(1, Math.min(100, limit));
    return query("SELECT * FROM monthly_assignment_tables ORDER BY created_at DESC LIMIT " + safeLimit);
  }

  public void markReady(long id, String documentId, String sheetId, String viewId, String uniqueFieldTitle, String documentUrl) {
    jdbcTemplate.update("""
        UPDATE monthly_assignment_tables
        SET document_id = ?, sheet_id = ?, view_id = ?, unique_field_title = ?, document_url = ?, status = 'READY',
            error_message = NULL, updated_at = NOW()
        WHERE id = ?
        """, documentId, sheetId, viewId, uniqueFieldTitle, documentUrl, id);
  }

  public void markDocumentCreated(long id, String documentId, String documentUrl) {
    jdbcTemplate.update("""
        UPDATE monthly_assignment_tables
        SET document_id = ?, document_url = ?, status = 'CREATING', error_message = NULL, updated_at = NOW()
        WHERE id = ?
        """, documentId, documentUrl, id);
  }

  public void markFailed(long id, String errorMessage) {
    jdbcTemplate.update("""
        UPDATE monthly_assignment_tables
        SET status = 'FAILED', error_message = ?, updated_at = NOW()
        WHERE id = ?
        """, truncate(errorMessage), id);
  }

  public void activate(long id) {
    jdbcTemplate.update("""
        UPDATE monthly_assignment_tables
        SET status = CASE WHEN id = ? THEN 'ACTIVE'
                          WHEN status = 'ACTIVE' THEN 'ARCHIVED'
                          ELSE status END,
            activated_at = CASE WHEN id = ? THEN NOW() ELSE activated_at END,
        updated_at = NOW()
        WHERE id = ? OR status = 'ACTIVE'
        """, id, id, id);
  }

  public int delete(long id) {
    return jdbcTemplate.update("DELETE FROM monthly_assignment_tables WHERE id = ?", id);
  }

  private List<MonthlyAssignmentTable> query(String sql, Object... args) {
    return jdbcTemplate.query(sql, this::map, args);
  }

  private MonthlyAssignmentTable map(ResultSet rs, int rowNum) throws SQLException {
    return new MonthlyAssignmentTable(
        rs.getLong("id"), rs.getString("table_name"), rs.getString("month_key"),
        rs.getString("document_id"), rs.getString("sheet_id"), rs.getString("view_id"),
        rs.getString("unique_field_title"), rs.getString("document_url"), rs.getString("status"), rs.getString("error_message"),
        rs.getString("created_by"), localDateTime(rs, "created_at"), localDateTime(rs, "activated_at"));
  }

  private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toLocalDateTime();
  }

  private String truncate(String value) {
    if (value == null || value.isBlank()) return "创建失败，请稍后重试";
    return value.length() <= 500 ? value : value.substring(0, 500);
  }
}
