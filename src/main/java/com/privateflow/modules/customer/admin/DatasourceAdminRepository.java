package com.privateflow.modules.customer.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.privateflow.modules.customer.sync.SheetSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DatasourceAdminRepository {

  private static final String DATASOURCE_INDEX = "idx_enabled";
  private static final String MAPPING_INDEX = "idx_source_target";
  private static final String VERSION_INDEX = "idx_datasource_ver";
  private final JdbcTemplate jdbcTemplate;

  public DatasourceAdminRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<Datasource> list() {
    return jdbcTemplate.query("""
        SELECT d.*,
               (SELECT COUNT(*) FROM datasource_field_mappings m WHERE m.source_table = d.source_table AND m.is_enabled = 1) AS mapping_count,
               (SELECT MAX(c.synced_at) FROM customers c WHERE c.source_table = d.source_table) AS last_sync_at,
               CASE
                 WHEN EXISTS(SELECT 1 FROM sync_failure_log f WHERE f.source_table = d.source_table AND f.resolved = 0) THEN 'ERROR'
                 ELSE 'OK'
               END AS sync_status
        FROM datasources d
        ORDER BY d.is_enabled DESC, d.id DESC
        """, this::mapDatasource);
  }

  public List<SheetSource> enabledSources() {
    return jdbcTemplate.query("""
        SELECT id, sheet_id, source_table
        FROM datasources
        WHERE is_enabled = 1
        ORDER BY id ASC
        """, (rs, rowNum) -> new SheetSource(
        rs.getLong("id"),
        rs.getString("sheet_id"),
        rs.getString("source_table")));
  }

  public Optional<SheetSource> defaultWriteSource() {
    return enabledSources().stream().findFirst();
  }

  public Optional<Datasource> find(long id) {
    return jdbcTemplate.query("""
        SELECT d.*,
               (SELECT COUNT(*) FROM datasource_field_mappings m WHERE m.source_table = d.source_table AND m.is_enabled = 1) AS mapping_count,
               (SELECT MAX(c.synced_at) FROM customers c WHERE c.source_table = d.source_table) AS last_sync_at,
               CASE
                 WHEN EXISTS(SELECT 1 FROM sync_failure_log f WHERE f.source_table = d.source_table AND f.resolved = 0) THEN 'ERROR'
                 ELSE 'OK'
               END AS sync_status
        FROM datasources d WHERE d.id = ? LIMIT 1
        """, this::mapDatasource, id).stream().findFirst();
  }

  public long create(DatasourceRequest request, String operator) {
    jdbcTemplate.update("""
        INSERT INTO datasources (name, sheet_id, source_table, description, is_enabled, created_by)
        VALUES (?, ?, ?, ?, 1, ?)
        """, request.name().trim(), request.sheetId().trim(), request.sourceTable().trim(), request.description(), operator);
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0 : id;
  }

  public boolean nameExists(String name, Long exceptId) {
    String except = exceptId == null ? "" : " AND id <> ?";
    Object[] args = exceptId == null
        ? new Object[] {name}
        : new Object[] {name, exceptId};
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM datasources WHERE name = ?" + except,
        Integer.class,
        args);
    return count != null && count > 0;
  }

  public void update(long id, DatasourceRequest request) {
    jdbcTemplate.update("""
        UPDATE datasources
        SET name = COALESCE(?, name),
            sheet_id = COALESCE(?, sheet_id),
            source_table = COALESCE(?, source_table),
            description = COALESCE(?, description),
            updated_at = NOW()
        WHERE id = ?
        """, blankToNull(request.name()), blankToNull(request.sheetId()), blankToNull(request.sourceTable()), request.description(), id);
  }

  public int delete(long id) {
    return jdbcTemplate.update("DELETE FROM datasources WHERE id = ?", id);
  }

  public int deleteMappings(String sourceTable) {
    return jdbcTemplate.update("DELETE FROM datasource_field_mappings WHERE source_table = ?", sourceTable);
  }

  public void toggle(long id, boolean enabled) {
    jdbcTemplate.update("UPDATE datasources SET is_enabled = ?, updated_at = NOW() WHERE id = ?", enabled ? 1 : 0, id);
  }

  public String replace(long id, String sheetId) {
    String old = jdbcTemplate.queryForObject("SELECT sheet_id FROM datasources WHERE id = ?", String.class, id);
    jdbcTemplate.update("UPDATE datasources SET sheet_id = ?, updated_at = NOW() WHERE id = ?", sheetId, id);
    return old;
  }

  public List<FieldMappingDto> mappings(String sourceTable) {
    return jdbcTemplate.query("""
        SELECT id, source_field, target_field, is_enabled
        FROM datasource_field_mappings
        WHERE source_table = ?
        ORDER BY id ASC
        """, (rs, rowNum) -> new FieldMappingDto(
            rs.getLong("id"),
            rs.getString("source_field"),
            rs.getString("target_field"),
            rs.getInt("is_enabled") == 1), sourceTable);
  }

  public void replaceMappings(String sourceTable, List<FieldMappingDto> mappings) {
    deleteMappings(sourceTable);
    for (FieldMappingDto mapping : mappings) {
      jdbcTemplate.update("""
          INSERT INTO datasource_field_mappings (source_table, source_field, target_field, is_enabled)
          VALUES (?, ?, ?, ?)
          """, sourceTable, mapping.sourceField().trim(), mapping.targetField().trim(), mapping.enabled() ? 1 : 0);
    }
  }

  public int currentMappingVersion(long datasourceId) {
    Integer version = jdbcTemplate.queryForObject(
        "SELECT COALESCE(MAX(version), 0) FROM datasource_mapping_versions WHERE datasource_id = ?",
        Integer.class,
        datasourceId);
    return version == null ? 0 : version;
  }

  public int createMappingVersion(long datasourceId, String mappingsJson, int count, String summary, String operator) {
    int version = currentMappingVersion(datasourceId) + 1;
    jdbcTemplate.update("""
        INSERT INTO datasource_mapping_versions (datasource_id, version, mappings_json, mapping_count, change_summary, changed_by)
        VALUES (?, ?, ?, ?, ?, ?)
        """, datasourceId, version, mappingsJson, count, summary, operator);
    return version;
  }

  public List<MappingVersionDto> mappingVersions(long datasourceId) {
    return jdbcTemplate.query("""
        SELECT version, mapping_count, changed_by, change_summary, created_at
        FROM datasource_mapping_versions
        WHERE datasource_id = ?
        ORDER BY version DESC
        LIMIT 20
        """, (rs, rowNum) -> new MappingVersionDto(
            rs.getInt("version"),
            rs.getInt("mapping_count"),
            rs.getString("changed_by"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getString("change_summary")), datasourceId);
  }

  public Optional<String> mappingSnapshot(long datasourceId, int version) {
    return jdbcTemplate.query("""
        SELECT mappings_json FROM datasource_mapping_versions
        WHERE datasource_id = ? AND version = ?
        LIMIT 1
        """, (rs, rowNum) -> rs.getString("mappings_json"), datasourceId, version).stream().findFirst();
  }

  public Optional<MappingSnapshot> latestMappingSnapshot(long datasourceId) {
    return jdbcTemplate.query("""
        SELECT version, mappings_json, mapping_count, changed_by, change_summary, created_at
        FROM datasource_mapping_versions
        WHERE datasource_id = ?
        ORDER BY version DESC
        LIMIT 1
        """, (rs, rowNum) -> new MappingSnapshot(
            rs.getInt("version"),
            rs.getString("mappings_json"),
            rs.getInt("mapping_count"),
            rs.getString("changed_by"),
            rs.getString("change_summary"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime()), datasourceId)
        .stream().findFirst();
  }

  public List<String> unresolvedFailures(String sourceTable) {
    return jdbcTemplate.query("""
        SELECT fail_reason FROM sync_failure_log
        WHERE source_table = ? AND resolved = 0
        ORDER BY created_at DESC
        LIMIT 100
        """, (rs, rowNum) -> rs.getString("fail_reason"), sourceTable);
  }

  public void logImport(String fileName, CsvImportResult result, String operator) {
    jdbcTemplate.update("""
        INSERT INTO customer_import_log (file_name, total_rows, created_count, updated_count, skipped_count, error_detail, imported_by)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """, fileName, result.totalRows(), result.created(), result.updated(), result.skipped(), result.errors().toString(), operator);
  }

  public List<Map<String, Object>> importLogs(int limit) {
    return jdbcTemplate.query("""
        SELECT id, file_name, total_rows, created_count, updated_count, skipped_count,
               error_detail, imported_by, created_at
        FROM customer_import_log
        ORDER BY created_at DESC, id DESC
        LIMIT ?
        """, (rs, rowNum) -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("id", rs.getLong("id"));
          item.put("fileName", rs.getString("file_name"));
          item.put("totalRows", rs.getInt("total_rows"));
          item.put("created", rs.getInt("created_count"));
          item.put("updated", rs.getInt("updated_count"));
          item.put("skipped", rs.getInt("skipped_count"));
          item.put("errorDetail", rs.getString("error_detail"));
          item.put("importedBy", rs.getString("imported_by"));
          item.put("createdAt", rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());
          return item;
        }, limit);
  }

  public long importLogCount() {
    Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer_import_log", Long.class);
    return count == null ? 0L : count;
  }

  private Datasource mapDatasource(ResultSet rs, int rowNum) throws SQLException {
    return new Datasource(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getString("sheet_id"),
        rs.getString("source_table"),
        rs.getString("description"),
        rs.getInt("is_enabled") == 1,
        rs.getInt("mapping_count"),
        rs.getTimestamp("last_sync_at") == null ? null : rs.getTimestamp("last_sync_at").toLocalDateTime(),
        rs.getString("sync_status"),
        rs.getString("created_by"),
        rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime());
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record MappingSnapshot(
      int version,
      String mappingsJson,
      int mappingCount,
      String changedBy,
      String changeSummary,
      LocalDateTime createdAt
  ) {
  }
}
