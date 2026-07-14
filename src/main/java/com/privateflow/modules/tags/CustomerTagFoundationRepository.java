package com.privateflow.modules.tags;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerTagFoundationRepository {

  private static final String TAG_DETAIL_SELECT = """
      SELECT
        a.*,
        c.id AS directory_category_id,
        c.category_key AS directory_category_key,
        c.category_name AS directory_category_name,
        c.selection_mode AS directory_category_selection_mode,
        c.is_enabled AS directory_category_enabled,
        c.merged_into_id AS directory_category_merged_into_id,
        c.version AS directory_category_version,
        v.id AS directory_value_id,
        v.category_id AS directory_value_category_id,
        v.tag_value AS directory_tag_value,
        v.display_name AS directory_tag_display_name,
        v.is_enabled AS directory_value_enabled,
        v.merged_into_id AS directory_value_merged_into_id,
        v.version AS directory_value_version
      FROM customer_tag_assignments a
      LEFT JOIN tag_categories c ON c.id = a.category_id
      LEFT JOIN tag_values v ON v.id = a.tag_value_id
      """;

  private final JdbcTemplate jdbcTemplate;

  public CustomerTagFoundationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<CustomerTagAssignment> findCurrentAssignments(long customerId) {
    return jdbcTemplate.query("""
        SELECT a.*
        FROM customer_tag_assignments a
        JOIN tag_categories c ON c.id = a.category_id
        JOIN tag_values v ON v.id = a.tag_value_id AND v.category_id = a.category_id
        WHERE a.customer_id = ? AND a.is_active = 1
          AND c.is_enabled = 1 AND c.merged_into_id IS NULL
          AND v.is_enabled = 1 AND v.merged_into_id IS NULL
        ORDER BY c.sort_order, v.sort_order, a.id
        """, this::mapAssignment, customerId);
  }

  public List<CustomerTagAssignment> findAssignmentHistory(long customerId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 1000));
    return jdbcTemplate.query("""
        SELECT * FROM customer_tag_assignments
        WHERE customer_id = ?
        ORDER BY created_at DESC, id DESC
        LIMIT ?
        """, this::mapAssignment, customerId, safeLimit);
  }

  public List<CustomerTagQueryDto> findCurrentTagDetails(long customerId) {
    return jdbcTemplate.query(TAG_DETAIL_SELECT + """
        WHERE a.customer_id = ? AND a.is_active = 1
          AND (
            c.id IS NULL
            OR v.id IS NULL
            OR v.category_id <> a.category_id
            OR (
              c.is_enabled = 1 AND c.merged_into_id IS NULL
              AND v.is_enabled = 1 AND v.merged_into_id IS NULL
            )
          )
        ORDER BY c.sort_order, v.sort_order, a.id
        """, this::mapTagDetail, customerId);
  }

  public List<CustomerTagQueryDto> findTagHistoryDetails(long customerId, int limit) {
    return jdbcTemplate.query(TAG_DETAIL_SELECT + """
        WHERE a.customer_id = ?
        ORDER BY a.created_at DESC, a.id DESC
        LIMIT ?
        """, this::mapTagDetail, customerId, limit);
  }

  public List<CustomerTagCategoryLock> findCategoryLocks(long customerId) {
    return jdbcTemplate.query("""
        SELECT * FROM customer_tag_category_locks
        WHERE customer_id = ?
        ORDER BY category_id
        """, this::mapLock, customerId);
  }

  public List<UnmatchedLegacyTagValue> findUnmatchedLegacyValues(long customerId) {
    return jdbcTemplate.query("""
        SELECT * FROM unmatched_legacy_tag_values
        WHERE customer_id = ?
        ORDER BY created_at, id
        """, this::mapUnmatched, customerId);
  }

  public List<TagLegacyValueMapping> findLegacyValueMappings(String sourceType) {
    return jdbcTemplate.query("""
        SELECT * FROM tag_legacy_value_mappings
        WHERE source_type = ?
        ORDER BY legacy_category_key, legacy_value, id
        """, this::mapLegacyMapping, sourceType);
  }

  public List<TagAnalysisRun> findAnalysisRuns(long customerId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 500));
    return jdbcTemplate.query("""
        SELECT * FROM tag_analysis_runs
        WHERE customer_id = ?
        ORDER BY created_at DESC, id DESC
        LIMIT ?
        """, this::mapAnalysisRun, customerId, safeLimit);
  }

  public List<TagAnalysisResult> findAnalysisResults(long analysisRunId) {
    return jdbcTemplate.query("""
        SELECT * FROM tag_analysis_results
        WHERE analysis_run_id = ?
        ORDER BY id
        """, this::mapAnalysisResult, analysisRunId);
  }

  private CustomerTagAssignment mapAssignment(ResultSet rs, int rowNum) throws SQLException {
    return new CustomerTagAssignment(
        rs.getLong("id"),
        rs.getLong("customer_id"),
        rs.getLong("category_id"),
        rs.getLong("tag_value_id"),
        TagSelectionMode.valueOf(rs.getString("selection_mode")),
        rs.getInt("is_active") == 1,
        rs.getString("source_type"),
        rs.getBigDecimal("confidence"),
        rs.getString("evidence_text"),
        rs.getInt("evidence_message_count"),
        nullableLong(rs, "analysis_result_id"),
        rs.getString("skill_id"),
        rs.getString("llm_environment"),
        rs.getString("llm_model"),
        rs.getString("prompt_version"),
        rs.getString("operator_account"),
        rs.getInt("is_manual_locked") == 1,
        rs.getString("locked_by"),
        localDateTime(rs, "locked_at"),
        nullableLong(rs, "supersedes_assignment_id"),
        rs.getInt("customer_version"),
        rs.getString("invalidated_reason"),
        localDateTime(rs, "invalidated_at"),
        localDateTime(rs, "created_at"),
        localDateTime(rs, "updated_at"),
        nullableLong(rs, "active_tag_key"),
        nullableLong(rs, "active_single_category_key"));
  }

  private CustomerTagQueryDto mapTagDetail(ResultSet rs, int rowNum) throws SQLException {
    long assignmentId = rs.getLong("id");
    long expectedCategoryId = rs.getLong("category_id");
    long expectedValueId = rs.getLong("tag_value_id");
    Long directoryCategoryId = nullableLong(rs, "directory_category_id");
    Long actualValueId = nullableLong(rs, "directory_value_id");
    Long actualValueCategoryId = nullableLong(rs, "directory_value_category_id");
    if (!Objects.equals(directoryCategoryId, expectedCategoryId)
        || !Objects.equals(actualValueId, expectedValueId)
        || !Objects.equals(actualValueCategoryId, expectedCategoryId)) {
      throw new IllegalStateException(
          "客户标签目录数据异常：assignmentId=" + assignmentId
              + "，期望categoryId=" + expectedCategoryId
              + "，目录categoryId=" + directoryCategoryId
              + "，期望valueId=" + expectedValueId
              + "，实际valueId=" + actualValueId
              + "，value实际categoryId=" + actualValueCategoryId);
    }
    return new CustomerTagQueryDto(
        assignmentId,
        rs.getLong("customer_id"),
        rs.getInt("customer_version"),
        directoryCategoryId,
        rs.getString("directory_category_key"),
        rs.getString("directory_category_name"),
        TagSelectionMode.valueOf(rs.getString("directory_category_selection_mode")),
        rs.getInt("directory_category_enabled") == 1,
        nullableLong(rs, "directory_category_merged_into_id"),
        rs.getInt("directory_category_version"),
        actualValueId,
        rs.getString("directory_tag_value"),
        rs.getString("directory_tag_display_name"),
        rs.getInt("directory_value_enabled") == 1,
        nullableLong(rs, "directory_value_merged_into_id"),
        rs.getInt("directory_value_version"),
        TagSelectionMode.valueOf(rs.getString("selection_mode")),
        rs.getInt("is_active") == 1,
        rs.getString("source_type"),
        rs.getBigDecimal("confidence"),
        rs.getString("evidence_text"),
        rs.getInt("evidence_message_count"),
        nullableLong(rs, "analysis_result_id"),
        rs.getString("skill_id"),
        rs.getString("llm_environment"),
        rs.getString("llm_model"),
        rs.getString("prompt_version"),
        rs.getString("operator_account"),
        rs.getInt("is_manual_locked") == 1,
        rs.getString("locked_by"),
        localDateTime(rs, "locked_at"),
        nullableLong(rs, "supersedes_assignment_id"),
        rs.getString("invalidated_reason"),
        localDateTime(rs, "invalidated_at"),
        localDateTime(rs, "created_at"),
        localDateTime(rs, "updated_at"));
  }

  private CustomerTagCategoryLock mapLock(ResultSet rs, int rowNum) throws SQLException {
    return new CustomerTagCategoryLock(
        rs.getLong("id"),
        rs.getLong("customer_id"),
        rs.getLong("category_id"),
        rs.getInt("is_locked") == 1,
        rs.getString("locked_by"),
        rs.getString("lock_reason"),
        localDateTime(rs, "locked_at"),
        rs.getString("unlocked_by"),
        localDateTime(rs, "unlocked_at"),
        rs.getInt("version"),
        localDateTime(rs, "created_at"),
        localDateTime(rs, "updated_at"));
  }

  private UnmatchedLegacyTagValue mapUnmatched(ResultSet rs, int rowNum) throws SQLException {
    return new UnmatchedLegacyTagValue(
        rs.getLong("id"),
        rs.getLong("customer_id"),
        rs.getString("source_type"),
        nullableLong(rs, "source_record_id"),
        rs.getString("legacy_field"),
        rs.getString("raw_value"),
        rs.getString("raw_value_hash"),
        nullableLong(rs, "category_id"),
        nullableLong(rs, "mapped_tag_value_id"),
        rs.getString("status"),
        rs.getString("resolution_note"),
        rs.getString("resolved_by"),
        localDateTime(rs, "resolved_at"),
        localDateTime(rs, "created_at"),
        localDateTime(rs, "updated_at"));
  }

  private TagLegacyValueMapping mapLegacyMapping(ResultSet rs, int rowNum) throws SQLException {
    return new TagLegacyValueMapping(
        rs.getLong("id"),
        rs.getString("source_type"),
        rs.getString("legacy_category_key"),
        rs.getString("legacy_value"),
        nullableLong(rs, "category_id"),
        nullableLong(rs, "tag_value_id"),
        rs.getString("mapping_status"),
        rs.getString("mapping_note"),
        localDateTime(rs, "created_at"),
        localDateTime(rs, "updated_at"));
  }

  private TagAnalysisRun mapAnalysisRun(ResultSet rs, int rowNum) throws SQLException {
    return new TagAnalysisRun(
        rs.getLong("id"),
        rs.getString("analysis_key"),
        rs.getLong("customer_id"),
        rs.getString("source_type"),
        rs.getString("status"),
        rs.getInt("effective_message_count"),
        rs.getInt("customer_version"),
        rs.getString("caller"),
        rs.getString("skill_id"),
        rs.getString("llm_environment"),
        rs.getString("llm_model"),
        rs.getString("prompt_version"),
        rs.getString("error_message"),
        localDateTime(rs, "started_at"),
        localDateTime(rs, "finished_at"),
        localDateTime(rs, "created_at"));
  }

  private TagAnalysisResult mapAnalysisResult(ResultSet rs, int rowNum) throws SQLException {
    return new TagAnalysisResult(
        rs.getLong("id"),
        rs.getLong("analysis_run_id"),
        rs.getLong("category_id"),
        nullableLong(rs, "tag_value_id"),
        rs.getString("result_type"),
        rs.getString("requested_action"),
        rs.getBigDecimal("confidence"),
        rs.getString("evidence_text"),
        rs.getString("validation_status"),
        rs.getString("validation_reason"),
        localDateTime(rs, "created_at"));
  }

  private Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private java.time.LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toLocalDateTime();
  }
}
