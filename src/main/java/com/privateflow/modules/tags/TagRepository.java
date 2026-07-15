package com.privateflow.modules.tags;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TagRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public TagRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public List<TagCategory> listTree() {
    List<TagCategory> categories = jdbcTemplate.query("""
        SELECT id, category_key, category_name, purpose, bound_field, selection_mode,
               system_inference_enabled, manual_edit_enabled, auto_update_mode,
               min_confidence, min_evidence_messages, cooldown_hours, uncertain_policy,
               use_for_reply, use_for_filter, use_for_statistics, use_for_followup_rules,
               is_builtin, is_enabled, sort_order, merged_into_id, version, created_at, updated_at
        FROM tag_categories
        ORDER BY sort_order ASC, id ASC
        """, (rs, rowNum) -> mapCategory(rs, List.of()));
    Map<Long, List<TagValue>> valuesByCategory = new LinkedHashMap<>();
    for (TagValue value : allValues()) {
      valuesByCategory.computeIfAbsent(value.categoryId(), ignored -> new ArrayList<>()).add(value);
    }
    return categories.stream()
        .map(category -> category.withValues(valuesByCategory.getOrDefault(category.id(), List.of())))
        .toList();
  }

  public TagCategoryPage searchCategories(
      String keyword,
      Boolean enabled,
      Boolean merged,
      TagSelectionMode selectionMode,
      Boolean builtin,
      int page,
      int size,
      String sortBy,
      String sortDirection) {
    int safePage = Math.max(1, page);
    int safeSize = Math.max(1, Math.min(size, 100));
    List<Object> args = new ArrayList<>();
    String where = categoryWhere(keyword, enabled, merged, selectionMode, builtin, args);
    Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tag_categories c " + where, Long.class, args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add((safePage - 1) * safeSize);
    pageArgs.add(safeSize);
    List<Long> ids = jdbcTemplate.queryForList(
        "SELECT c.id FROM tag_categories c " + where + " ORDER BY "
            + categoryOrder(sortBy, sortDirection) + " LIMIT ?, ?",
        Long.class,
        pageArgs.toArray());
    List<TagCategory> items = ids.stream().map(id -> findCategory(id).orElseThrow()).toList();
    long safeTotal = total == null ? 0 : total;
    return new TagCategoryPage(
        safePage,
        safeSize,
        safeTotal,
        Math.max(1, (int) Math.ceil(safeTotal / (double) safeSize)),
        items);
  }

  public List<TagCategory> findCategoriesForExport(
      String keyword,
      Boolean enabled,
      Boolean merged,
      TagSelectionMode selectionMode,
      Boolean builtin,
      String sortBy,
      String sortDirection) {
    List<Object> args = new ArrayList<>();
    String where = categoryWhere(keyword, enabled, merged, selectionMode, builtin, args);
    List<Long> ids = jdbcTemplate.queryForList(
        "SELECT c.id FROM tag_categories c " + where + " ORDER BY " + categoryOrder(sortBy, sortDirection),
        Long.class,
        args.toArray());
    return ids.stream().map(id -> findCategory(id).orElseThrow()).toList();
  }

  public Optional<TagCategory> findCategory(long id) {
    return jdbcTemplate.query("""
        SELECT id, category_key, category_name, purpose, bound_field, selection_mode,
               system_inference_enabled, manual_edit_enabled, auto_update_mode,
               min_confidence, min_evidence_messages, cooldown_hours, uncertain_policy,
               use_for_reply, use_for_filter, use_for_statistics, use_for_followup_rules,
               is_builtin, is_enabled, sort_order, merged_into_id, version, created_at, updated_at
        FROM tag_categories WHERE id = ? LIMIT 1
        """, (rs, rowNum) -> mapCategory(rs, values(id)), id).stream().findFirst();
  }

  public Optional<TagValue> findValue(long id) {
    return jdbcTemplate.query("""
        SELECT v.id, v.category_id, c.category_key, v.tag_value, v.display_name,
               v.meaning, v.applicable_when, v.not_applicable_when,
               v.positive_examples, v.negative_examples, v.synonyms_json,
               v.system_selectable, v.manual_selectable, v.is_enabled,
               v.sort_order, v.merged_into_id, v.version, v.created_at, v.updated_at
        FROM tag_values v JOIN tag_categories c ON c.id = v.category_id
        WHERE v.id = ? LIMIT 1
        """, this::mapValue, id).stream().findFirst();
  }

  public Optional<TagValue> findValueByCategoryAndCode(long categoryId, String tagValue) {
    return jdbcTemplate.query("""
        SELECT v.id, v.category_id, c.category_key, v.tag_value, v.display_name,
               v.meaning, v.applicable_when, v.not_applicable_when,
               v.positive_examples, v.negative_examples, v.synonyms_json,
               v.system_selectable, v.manual_selectable, v.is_enabled,
               v.sort_order, v.merged_into_id, v.version, v.created_at, v.updated_at
        FROM tag_values v JOIN tag_categories c ON c.id = v.category_id
        WHERE v.category_id = ? AND v.tag_value = ? LIMIT 1
        """, this::mapValue, categoryId, tagValue).stream().findFirst();
  }

  public TagValuePage searchValues(
      Long categoryId,
      String keyword,
      Boolean enabled,
      Boolean merged,
      Boolean systemSelectable,
      Boolean manualSelectable,
      int page,
      int size,
      String sortBy,
      String sortDirection) {
    int safePage = Math.max(1, page);
    int safeSize = Math.max(1, Math.min(size, 100));
    List<Object> args = new ArrayList<>();
    String where = valueWhere(categoryId, keyword, enabled, merged, systemSelectable, manualSelectable, args);
    Long total = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tag_values v JOIN tag_categories c ON c.id = v.category_id " + where,
        Long.class,
        args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add((safePage - 1) * safeSize);
    pageArgs.add(safeSize);
    List<Long> ids = jdbcTemplate.queryForList(
        "SELECT v.id FROM tag_values v JOIN tag_categories c ON c.id = v.category_id " + where
            + " ORDER BY " + valueOrder(sortBy, sortDirection) + " LIMIT ?, ?",
        Long.class,
        pageArgs.toArray());
    List<TagValue> items = ids.stream().map(id -> findValue(id).orElseThrow()).toList();
    long safeTotal = total == null ? 0 : total;
    return new TagValuePage(
        safePage,
        safeSize,
        safeTotal,
        Math.max(1, (int) Math.ceil(safeTotal / (double) safeSize)),
        items);
  }

  public List<TagValue> findValuesForExport(
      Long categoryId,
      String keyword,
      Boolean enabled,
      Boolean merged,
      Boolean systemSelectable,
      Boolean manualSelectable,
      String sortBy,
      String sortDirection) {
    List<Object> args = new ArrayList<>();
    String where = valueWhere(categoryId, keyword, enabled, merged, systemSelectable, manualSelectable, args);
    List<Long> ids = jdbcTemplate.queryForList(
        "SELECT v.id FROM tag_values v JOIN tag_categories c ON c.id = v.category_id " + where
            + " ORDER BY " + valueOrder(sortBy, sortDirection),
        Long.class,
        args.toArray());
    return ids.stream().map(id -> findValue(id).orElseThrow()).toList();
  }

  public boolean boundFieldExists(String boundField) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tag_categories WHERE bound_field = ?", Integer.class, boundField);
    return count != null && count > 0;
  }

  public boolean categoryKeyExists(String categoryKey) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tag_categories WHERE category_key = ?", Integer.class, categoryKey);
    return count != null && count > 0;
  }

  public boolean valueExists(long categoryId, String tagValue) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tag_values WHERE category_id = ? AND tag_value = ?",
        Integer.class,
        categoryId,
        tagValue);
    return count != null && count > 0;
  }

  public int valueCount(long categoryId) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tag_values WHERE category_id = ?", Integer.class, categoryId);
    return count == null ? 0 : count;
  }

  public List<TagValue> listValuesByCategory(long categoryId) {
    return values(categoryId);
  }

  public TagImpact categoryImpact(long categoryId) {
    Map<String, Object> metrics = jdbcTemplate.queryForMap("""
        SELECT
          (SELECT COUNT(DISTINCT customer_id) FROM customer_tag_assignments WHERE category_id = ?) AS customer_count,
          (SELECT COUNT(*) FROM customer_tag_assignments WHERE category_id = ?) AS assignment_count,
          (SELECT COUNT(*)
             FROM customer_tag_assignments a
             JOIN tag_categories c ON c.id = a.category_id
             JOIN tag_values v ON v.id = a.tag_value_id AND v.category_id = a.category_id
            WHERE a.category_id = ? AND a.is_active = 1
              AND c.is_enabled = 1 AND c.merged_into_id IS NULL
              AND v.is_enabled = 1 AND v.merged_into_id IS NULL) AS active_assignment_count,
          (SELECT COUNT(*) FROM tag_analysis_results WHERE category_id = ?) AS analysis_count,
          (SELECT COUNT(*) FROM tag_legacy_value_mappings WHERE category_id = ?) AS legacy_mapping_count,
          (SELECT COUNT(*) FROM system_tag_suggestions s JOIN tag_values v ON v.id = s.tag_value_id WHERE v.category_id = ?) AS suggestion_count,
          (SELECT COUNT(*) FROM unmatched_legacy_tag_values WHERE category_id = ?) AS unmatched_count,
          (SELECT COUNT(*) FROM customer_tag_category_locks WHERE category_id = ?) AS lock_count,
          (SELECT COUNT(*) FROM personality_tags p JOIN tag_values v ON v.id = p.canonical_tag_value_id WHERE v.category_id = ?) AS personality_count
        """, categoryId, categoryId, categoryId, categoryId, categoryId, categoryId, categoryId, categoryId, categoryId);
    long customers = number(metrics, "customer_count");
    long assignments = number(metrics, "assignment_count");
    long activeAssignments = number(metrics, "active_assignment_count");
    long analysis = number(metrics, "analysis_count");
    long suggestions = number(metrics, "suggestion_count");
    long mappings = number(metrics, "legacy_mapping_count");
    long unmatched = number(metrics, "unmatched_count");
    long locks = number(metrics, "lock_count");
    long personality = number(metrics, "personality_count");
    return new TagImpact(
        customers,
        0,
        assignments + analysis + suggestions + mappings + unmatched + locks + personality,
        activeAssignments,
        analysis,
        suggestions,
        mappings,
        unmatched,
        locks);
  }

  public TagImpact valueImpact(TagValue value) {
    Map<String, Object> metrics = jdbcTemplate.queryForMap("""
        SELECT
          (SELECT COUNT(DISTINCT customer_id) FROM customer_tag_assignments WHERE tag_value_id = ?) AS customer_count,
          (SELECT COUNT(*) FROM customer_tag_assignments WHERE tag_value_id = ?) AS assignment_count,
          (SELECT COUNT(*)
             FROM customer_tag_assignments a
             JOIN tag_categories c ON c.id = a.category_id
             JOIN tag_values v ON v.id = a.tag_value_id AND v.category_id = a.category_id
            WHERE a.tag_value_id = ? AND a.is_active = 1
              AND c.is_enabled = 1 AND c.merged_into_id IS NULL
              AND v.is_enabled = 1 AND v.merged_into_id IS NULL) AS active_assignment_count,
          (SELECT COUNT(*) FROM tag_analysis_results WHERE tag_value_id = ?) AS analysis_count,
          (SELECT COUNT(*) FROM tag_legacy_value_mappings WHERE tag_value_id = ?) AS legacy_mapping_count,
          (SELECT COUNT(*) FROM system_tag_suggestions WHERE tag_value_id = ?) AS suggestion_count,
          (SELECT COUNT(*) FROM unmatched_legacy_tag_values WHERE mapped_tag_value_id = ?) AS unmatched_count,
          (SELECT COUNT(*) FROM personality_tags WHERE canonical_tag_value_id = ?) AS personality_count
        """, value.id(), value.id(), value.id(), value.id(), value.id(), value.id(), value.id(), value.id());
    long customers = number(metrics, "customer_count");
    long assignments = number(metrics, "assignment_count");
    long activeAssignments = number(metrics, "active_assignment_count");
    long analysis = number(metrics, "analysis_count");
    long suggestions = number(metrics, "suggestion_count");
    long mappings = number(metrics, "legacy_mapping_count");
    long unmatched = number(metrics, "unmatched_count");
    long personality = number(metrics, "personality_count");
    return new TagImpact(
        customers,
        0,
        assignments + analysis + suggestions + mappings + unmatched + personality,
        activeAssignments,
        analysis,
        suggestions,
        mappings,
        unmatched,
        0);
  }

  public long createCategory(String categoryKey, TagCategoryRequest request, int sortOrder) {
    jdbcTemplate.update("""
        INSERT INTO tag_categories (
          category_key, category_name, purpose, bound_field, selection_mode,
          system_inference_enabled, manual_edit_enabled, auto_update_mode,
          min_confidence, min_evidence_messages, cooldown_hours, uncertain_policy,
          use_for_reply, use_for_filter, use_for_statistics, use_for_followup_rules,
          is_builtin, is_enabled, sort_order
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
        """,
        categoryKey,
        request.categoryName().trim(),
        defaultString(request.purpose(), ""),
        nullableTrim(request.boundField()),
        defaultEnum(request.selectionMode(), TagSelectionMode.SINGLE).name(),
        bool(request.systemInferenceEnabled(), false),
        bool(request.manualEditEnabled(), true),
        defaultEnum(request.autoUpdateMode(), TagAutoUpdateMode.RECORD_ONLY).name(),
        request.minConfidence() == null ? new java.math.BigDecimal("0.8500") : request.minConfidence(),
        request.minEvidenceMessages() == null ? 1 : request.minEvidenceMessages(),
        request.cooldownHours() == null ? 0 : request.cooldownHours(),
        defaultEnum(request.uncertainPolicy(), TagUncertainPolicy.KEEP_CURRENT).name(),
        bool(request.useForReply(), true),
        bool(request.useForFilter(), true),
        bool(request.useForStatistics(), true),
        bool(request.useForFollowupRules(), true),
        bool(request.isEnabled(), true),
        sortOrder);
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0L : id;
  }

  public int updateCategory(long id, TagCategoryRequest request) {
    return jdbcTemplate.update("""
        UPDATE tag_categories
        SET category_name = COALESCE(?, category_name),
            purpose = COALESCE(?, purpose),
            selection_mode = COALESCE(?, selection_mode),
            system_inference_enabled = COALESCE(?, system_inference_enabled),
            manual_edit_enabled = COALESCE(?, manual_edit_enabled),
            auto_update_mode = COALESCE(?, auto_update_mode),
            min_confidence = COALESCE(?, min_confidence),
            min_evidence_messages = COALESCE(?, min_evidence_messages),
            cooldown_hours = COALESCE(?, cooldown_hours),
            uncertain_policy = COALESCE(?, uncertain_policy),
            use_for_reply = COALESCE(?, use_for_reply),
            use_for_filter = COALESCE(?, use_for_filter),
            use_for_statistics = COALESCE(?, use_for_statistics),
            use_for_followup_rules = COALESCE(?, use_for_followup_rules),
            is_enabled = COALESCE(?, is_enabled),
            sort_order = COALESCE(?, sort_order),
            version = version + 1,
            updated_at = NOW()
        WHERE id = ? AND version = ?
        """,
        blankToNull(request.categoryName()),
        trimIfPresent(request.purpose()),
        enumName(request.selectionMode()),
        nullableBool(request.systemInferenceEnabled()),
        nullableBool(request.manualEditEnabled()),
        enumName(request.autoUpdateMode()),
        request.minConfidence(),
        request.minEvidenceMessages(),
        request.cooldownHours(),
        enumName(request.uncertainPolicy()),
        nullableBool(request.useForReply()),
        nullableBool(request.useForFilter()),
        nullableBool(request.useForStatistics()),
        nullableBool(request.useForFollowupRules()),
        request.isEnabled() == null ? null : (request.isEnabled() ? 1 : 0),
        request.sortOrder(),
        id,
        request.version());
  }

  public int toggleCategory(long id, boolean enabled, Integer version) {
    return jdbcTemplate.update("""
        UPDATE tag_categories
        SET is_enabled = ?, version = version + 1, updated_at = NOW()
        WHERE id = ? AND version = ?
        """, enabled ? 1 : 0, id, version);
  }

  public int deleteCategory(long id) {
    return jdbcTemplate.update("DELETE FROM tag_categories WHERE id = ? AND is_builtin = 0", id);
  }

  public long createValue(String tagValue, TagValueRequest request, int sortOrder) {
    int inserted = jdbcTemplate.update("""
        INSERT INTO tag_values (
          category_id, tag_value, display_name, meaning, applicable_when,
          not_applicable_when, positive_examples, negative_examples, synonyms_json,
          system_selectable, manual_selectable, is_enabled, sort_order
        )
        SELECT c.id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
        FROM tag_categories c
        WHERE c.id = ? AND c.is_enabled = 1 AND c.merged_into_id IS NULL
        """,
        tagValue,
        request.displayName().trim(),
        defaultString(request.meaning(), ""),
        defaultString(request.applicableWhen(), ""),
        defaultString(request.notApplicableWhen(), ""),
        defaultString(request.positiveExamples(), ""),
        defaultString(request.negativeExamples(), ""),
        synonymsJson(request.synonyms(), "[]"),
        bool(request.systemSelectable(), false),
        bool(request.manualSelectable(), true),
        bool(request.isEnabled(), true),
        sortOrder,
        request.categoryId());
    if (inserted == 0) {
      return 0L;
    }
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0L : id;
  }

  public int updateValue(long id, TagValueRequest request) {
    return jdbcTemplate.update("""
        UPDATE tag_values
        SET display_name = COALESCE(?, display_name),
            meaning = COALESCE(?, meaning),
            applicable_when = COALESCE(?, applicable_when),
            not_applicable_when = COALESCE(?, not_applicable_when),
            positive_examples = COALESCE(?, positive_examples),
            negative_examples = COALESCE(?, negative_examples),
            synonyms_json = COALESCE(?, synonyms_json),
            system_selectable = COALESCE(?, system_selectable),
            manual_selectable = COALESCE(?, manual_selectable),
            is_enabled = COALESCE(?, is_enabled),
            sort_order = COALESCE(?, sort_order),
            version = version + 1,
            updated_at = NOW()
        WHERE id = ? AND version = ?
        """,
        blankToNull(request.displayName()),
        trimIfPresent(request.meaning()),
        trimIfPresent(request.applicableWhen()),
        trimIfPresent(request.notApplicableWhen()),
        trimIfPresent(request.positiveExamples()),
        trimIfPresent(request.negativeExamples()),
        synonymsJson(request.synonyms(), null),
        nullableBool(request.systemSelectable()),
        nullableBool(request.manualSelectable()),
        request.isEnabled() == null ? null : (request.isEnabled() ? 1 : 0),
        request.sortOrder(),
        id,
        request.version());
  }

  public int toggleValue(long id, boolean enabled, Integer version) {
    return jdbcTemplate.update("""
        UPDATE tag_values
        SET is_enabled = ?, version = version + 1, updated_at = NOW()
        WHERE id = ? AND version = ?
        """, enabled ? 1 : 0, id, version);
  }

  public int deleteValue(long id) {
    return jdbcTemplate.update("DELETE FROM tag_values WHERE id = ?", id);
  }

  private List<TagValue> values(long categoryId) {
    return jdbcTemplate.query("""
        SELECT v.id, v.category_id, c.category_key, v.tag_value, v.display_name,
               v.meaning, v.applicable_when, v.not_applicable_when,
               v.positive_examples, v.negative_examples, v.synonyms_json,
               v.system_selectable, v.manual_selectable, v.is_enabled,
               v.sort_order, v.merged_into_id, v.version, v.created_at, v.updated_at
        FROM tag_values v JOIN tag_categories c ON c.id = v.category_id
        WHERE v.category_id = ?
        ORDER BY v.sort_order ASC, v.id ASC
        """, this::mapValue, categoryId);
  }

  private List<TagValue> allValues() {
    return jdbcTemplate.query("""
        SELECT v.id, v.category_id, c.category_key, v.tag_value, v.display_name,
               v.meaning, v.applicable_when, v.not_applicable_when,
               v.positive_examples, v.negative_examples, v.synonyms_json,
               v.system_selectable, v.manual_selectable, v.is_enabled,
               v.sort_order, v.merged_into_id, v.version, v.created_at, v.updated_at
        FROM tag_values v JOIN tag_categories c ON c.id = v.category_id
        ORDER BY c.sort_order ASC, c.id ASC, v.sort_order ASC, v.id ASC
        """, this::mapValue);
  }

  private TagCategory mapCategory(ResultSet rs, List<TagValue> values) throws SQLException {
    return new TagCategory(
        rs.getLong("id"),
        rs.getString("category_key"),
        rs.getString("category_name"),
        rs.getString("purpose"),
        rs.getString("bound_field"),
        TagSelectionMode.valueOf(rs.getString("selection_mode")),
        rs.getInt("system_inference_enabled") == 1,
        rs.getInt("manual_edit_enabled") == 1,
        TagAutoUpdateMode.valueOf(rs.getString("auto_update_mode")),
        rs.getBigDecimal("min_confidence"),
        rs.getInt("min_evidence_messages"),
        rs.getInt("cooldown_hours"),
        TagUncertainPolicy.valueOf(rs.getString("uncertain_policy")),
        rs.getInt("use_for_reply") == 1,
        rs.getInt("use_for_filter") == 1,
        rs.getInt("use_for_statistics") == 1,
        rs.getInt("use_for_followup_rules") == 1,
        rs.getInt("is_builtin") == 1,
        rs.getInt("is_enabled") == 1,
        rs.getInt("sort_order"),
        nullableLong(rs, "merged_into_id"),
        rs.getInt("version"),
        values,
        TagImpact.empty(),
        rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime());
  }

  private TagValue mapValue(ResultSet rs, int rowNum) throws SQLException {
    return new TagValue(
        rs.getLong("id"),
        rs.getLong("category_id"),
        rs.getString("category_key"),
        rs.getString("tag_value"),
        rs.getString("display_name"),
        rs.getString("meaning"),
        rs.getString("applicable_when"),
        rs.getString("not_applicable_when"),
        rs.getString("positive_examples"),
        rs.getString("negative_examples"),
        parseSynonyms(rs.getString("synonyms_json")),
        rs.getInt("system_selectable") == 1,
        rs.getInt("manual_selectable") == 1,
        rs.getInt("is_enabled") == 1,
        rs.getInt("sort_order"),
        nullableLong(rs, "merged_into_id"),
        rs.getInt("version"),
        TagImpact.empty(),
        rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime());
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String trimIfPresent(String value) {
    return value == null ? null : value.trim();
  }

  private String nullableTrim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String defaultString(String value, String fallback) {
    return value == null ? fallback : value.trim();
  }

  private int bool(Boolean value, boolean fallback) {
    return Boolean.TRUE.equals(value == null ? fallback : value) ? 1 : 0;
  }

  private Integer nullableBool(Boolean value) {
    return value == null ? null : (value ? 1 : 0);
  }

  private <T extends Enum<T>> T defaultEnum(T value, T fallback) {
    return value == null ? fallback : value;
  }

  private String enumName(Enum<?> value) {
    return value == null ? null : value.name();
  }

  private String synonymsJson(List<String> synonyms, String fallback) {
    if (synonyms == null) {
      return fallback;
    }
    try {
      return objectMapper.writeValueAsString(synonyms);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("标签同义表达格式不正确", ex);
    }
  }

  private List<String> parseSynonyms(String raw) {
    try {
      return List.copyOf(objectMapper.readValue(raw, new TypeReference<List<String>>() {}));
    } catch (Exception ex) {
      throw new IllegalStateException("数据库中的标签同义表达不是有效 JSON 数组", ex);
    }
  }

  private Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private String toSnakeCase(String camel) {
    return camel.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
  }

  private String categoryWhere(
      String keyword,
      Boolean enabled,
      Boolean merged,
      TagSelectionMode selectionMode,
      Boolean builtin,
      List<Object> args) {
    StringBuilder where = new StringBuilder(" WHERE 1=1 ");
    if (keyword != null && !keyword.isBlank()) {
      String like = "%" + keyword.trim() + "%";
      where.append(" AND (c.category_name LIKE ? OR c.category_key LIKE ? OR c.purpose LIKE ?) ");
      args.add(like);
      args.add(like);
      args.add(like);
    }
    if (enabled != null) {
      where.append(" AND c.is_enabled = ? ");
      args.add(enabled ? 1 : 0);
    }
    if (merged != null) {
      where.append(merged ? " AND c.merged_into_id IS NOT NULL " : " AND c.merged_into_id IS NULL ");
    }
    if (selectionMode != null) {
      where.append(" AND c.selection_mode = ? ");
      args.add(selectionMode.name());
    }
    if (builtin != null) {
      where.append(" AND c.is_builtin = ? ");
      args.add(builtin ? 1 : 0);
    }
    return where.toString();
  }

  private String valueWhere(
      Long categoryId,
      String keyword,
      Boolean enabled,
      Boolean merged,
      Boolean systemSelectable,
      Boolean manualSelectable,
      List<Object> args) {
    StringBuilder where = new StringBuilder(" WHERE 1=1 ");
    if (categoryId != null) {
      where.append(" AND v.category_id = ? ");
      args.add(categoryId);
    }
    if (keyword != null && !keyword.isBlank()) {
      String like = "%" + keyword.trim() + "%";
      where.append(" AND (v.display_name LIKE ? OR v.tag_value LIKE ? OR v.meaning LIKE ? OR v.synonyms_json LIKE ?) ");
      args.add(like);
      args.add(like);
      args.add(like);
      args.add(like);
    }
    if (enabled != null) {
      where.append(" AND v.is_enabled = ? ");
      args.add(enabled ? 1 : 0);
    }
    if (merged != null) {
      where.append(merged ? " AND v.merged_into_id IS NOT NULL " : " AND v.merged_into_id IS NULL ");
    }
    if (systemSelectable != null) {
      where.append(" AND v.system_selectable = ? ");
      args.add(systemSelectable ? 1 : 0);
    }
    if (manualSelectable != null) {
      where.append(" AND v.manual_selectable = ? ");
      args.add(manualSelectable ? 1 : 0);
    }
    return where.toString();
  }

  private String categoryOrder(String sortBy, String direction) {
    String column = switch (sortBy == null ? "" : sortBy) {
      case "categoryName" -> "c.category_name";
      case "createdAt" -> "c.created_at";
      case "updatedAt" -> "c.updated_at";
      case "id" -> "c.id";
      default -> "c.sort_order";
    };
    return column + sortDirection(direction) + ", c.id ASC";
  }

  private String valueOrder(String sortBy, String direction) {
    String column = switch (sortBy == null ? "" : sortBy) {
      case "displayName" -> "v.display_name";
      case "createdAt" -> "v.created_at";
      case "updatedAt" -> "v.updated_at";
      case "id" -> "v.id";
      default -> "v.sort_order";
    };
    return column + sortDirection(direction) + ", v.id ASC";
  }

  private String sortDirection(String direction) {
    return "DESC".equalsIgnoreCase(direction) ? " DESC" : " ASC";
  }

  private long number(Map<String, Object> metrics, String key) {
    Object value = metrics.get(key);
    return value instanceof Number number ? number.longValue() : 0L;
  }

}
