package com.privateflow.modules.api.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

  private final JdbcTemplate jdbcTemplate;

  public AuditLogRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<AuditLogEntry> list(AuditLogQuery query) {
    List<Object> args = new ArrayList<>();
    String where = where(query, args);
    args.add(query.size());
    args.add((query.page() - 1) * query.size());
    return jdbcTemplate.query("""
        SELECT id, operator, action, target_type, target_id, detail, created_at
        FROM audit_logs
        """ + where + """
        ORDER BY created_at DESC, id DESC
        LIMIT ? OFFSET ?
        """, this::map, args.toArray());
  }

  public List<AuditLogEntry> exportRows(AuditLogQuery query, int maxRows) {
    List<Object> args = new ArrayList<>();
    String where = where(query, args);
    args.add(maxRows);
    return jdbcTemplate.query("""
        SELECT id, operator, action, target_type, target_id, detail, created_at
        FROM audit_logs
        """ + where + """
        ORDER BY created_at DESC, id DESC
        LIMIT ?
        """, this::map, args.toArray());
  }

  public long count(AuditLogQuery query) {
    List<Object> args = new ArrayList<>();
    Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs " + where(query, args), Long.class, args.toArray());
    return value == null ? 0L : value;
  }

  public Optional<LocalDateTime> earliest() {
    return jdbcTemplate.query("SELECT MIN(created_at) AS created_at FROM audit_logs",
        (rs, rowNum) -> rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime())
        .stream().filter(Objects::nonNull).findFirst();
  }

  public void createExport(String exportId, String filtersJson, long totalCount, LocalDateTime expireAt, String createdBy) {
    jdbcTemplate.update("""
        INSERT INTO audit_log_exports (export_id, status, filters_json, total_count, expire_at, created_by, message)
        VALUES (?, 'PROCESSING', ?, ?, ?, ?, ?)
        """, exportId, filtersJson, totalCount, expireAt, createdBy, "正在生成 CSV 文件，请稍后查看下载链接");
  }

  public Optional<AuditExportRecord> findExport(String exportId) {
    return jdbcTemplate.query("""
        SELECT id, export_id, status, filters_json, total_count, csv_content, download_url, message,
               expire_at, created_by, created_at, completed_at
        FROM audit_log_exports
        WHERE export_id = ?
        LIMIT 1
        """, this::mapExport, exportId).stream().findFirst();
  }

  public void completeExport(String exportId, String csvContent, String downloadUrl) {
    jdbcTemplate.update("""
        UPDATE audit_log_exports
        SET status = 'COMPLETED',
            csv_content = ?,
            download_url = ?,
            message = 'CSV 生成完成',
            completed_at = NOW()
        WHERE export_id = ?
        """, csvContent, downloadUrl, exportId);
  }

  public void failExport(String exportId, String message) {
    jdbcTemplate.update("""
        UPDATE audit_log_exports
        SET status = 'FAILED',
            message = ?,
            completed_at = NOW()
        WHERE export_id = ?
        """, message == null ? "CSV 生成失败" : truncate(message, 500), exportId);
  }

  public int cleanupExports(int retentionHours) {
    return jdbcTemplate.update("""
        DELETE FROM audit_log_exports
        WHERE expire_at < DATE_SUB(NOW(), INTERVAL ? HOUR)
        """, retentionHours);
  }

  public int cleanupAuditLogs(int retentionDays, int batchSize) {
    return jdbcTemplate.update("""
        DELETE FROM audit_logs
        WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)
        LIMIT ?
        """, retentionDays, batchSize);
  }

  private String where(AuditLogQuery query, List<Object> args) {
    StringBuilder sql = new StringBuilder(" WHERE created_at >= ? AND created_at <= ? ");
    args.add(query.startDate().atStartOfDay());
    args.add(query.endDate().plusDays(1).atStartOfDay().minusNanos(1));
    if (query.actions() != null && !query.actions().isEmpty()) {
      sql.append(" AND action IN (");
      for (int i = 0; i < query.actions().size(); i++) {
        if (i > 0) {
          sql.append(", ");
        }
        sql.append("?");
        args.add(query.actions().get(i));
      }
      sql.append(") ");
    }
    if (query.operator() != null && !query.operator().isBlank()) {
      sql.append(" AND operator LIKE ? ");
      args.add(query.operator().trim() + "%");
    }
    if (query.targetType() != null && !query.targetType().isBlank()) {
      sql.append(" AND target_type = ? ");
      args.add(query.targetType().trim());
    }
    if (query.targetId() != null && !query.targetId().isBlank()) {
      sql.append(" AND target_id = ? ");
      args.add(query.targetId().trim());
    }
    if (query.keyword() != null && !query.keyword().isBlank()) {
      sql.append(" AND (operator LIKE ? OR detail LIKE ? OR target_id LIKE ? OR target_type LIKE ? OR action LIKE ?) ");
      String like = "%" + query.keyword().trim() + "%";
      args.add(like);
      args.add(like);
      args.add(like);
      args.add(like);
      args.add(like);
    }
    return sql.toString();
  }

  private AuditLogEntry map(ResultSet rs, int rowNum) throws SQLException {
    return new AuditLogEntry(
        rs.getLong("id"),
        rs.getString("operator"),
        rs.getString("action"),
        rs.getString("target_type"),
        rs.getString("target_id"),
        rs.getString("detail"),
        rs.getTimestamp("created_at").toLocalDateTime());
  }

  private AuditExportRecord mapExport(ResultSet rs, int rowNum) throws SQLException {
    return new AuditExportRecord(
        rs.getLong("id"),
        rs.getString("export_id"),
        AuditExportStatus.valueOf(rs.getString("status")),
        rs.getString("filters_json"),
        rs.getLong("total_count"),
        rs.getString("csv_content"),
        rs.getString("download_url"),
        rs.getString("message"),
        rs.getTimestamp("expire_at").toLocalDateTime(),
        rs.getString("created_by"),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime());
  }

  private String truncate(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max);
  }
}
