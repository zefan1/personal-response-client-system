package com.privateflow.modules.tags;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TagMergeRepository {

  private final JdbcTemplate jdbcTemplate;

  public TagMergeRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<Integer> lockCategory(long id) {
    return jdbcTemplate.queryForList(
        "SELECT version FROM tag_categories WHERE id = ? FOR UPDATE",
        Integer.class,
        id).stream().findFirst();
  }

  public Optional<Integer> lockValue(long id) {
    return jdbcTemplate.queryForList(
        "SELECT version FROM tag_values WHERE id = ? FOR UPDATE",
        Integer.class,
        id).stream().findFirst();
  }

  public int transferBoundField(TagCategory source, TagCategory target) {
    return jdbcTemplate.update("""
        UPDATE tag_categories
        SET bound_field = CASE WHEN id = ? THEN ? ELSE NULL END,
            version = version + 1,
            updated_at = NOW()
        WHERE (id = ? AND bound_field IS NULL)
           OR (id = ? AND bound_field = ?)
        """,
        target.id(), source.boundField(),
        target.id(), source.id(), source.boundField());
  }

  public long cloneValue(long sourceValueId, long targetCategoryId) {
    jdbcTemplate.update("""
        INSERT INTO tag_values (
          category_id, tag_value, display_name, meaning, applicable_when,
          not_applicable_when, positive_examples, negative_examples, synonyms_json,
          system_selectable, manual_selectable, is_enabled, sort_order, merged_into_id, version
        )
        SELECT ?, tag_value, display_name, meaning, applicable_when,
               not_applicable_when, positive_examples, negative_examples, synonyms_json,
               system_selectable, manual_selectable, is_enabled, sort_order, NULL, 0
        FROM tag_values
        WHERE id = ?
        """, targetCategoryId, sourceValueId);
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0L : id;
  }

  public Set<String> mergeValueReferences(
      TagValue source,
      TagCategory sourceCategory,
      TagValue target,
      TagCategory targetCategory,
      String reason) {
    List<CustomerRef> affectedCustomers = jdbcTemplate.query("""
        SELECT DISTINCT c.id, c.phone
        FROM customer_tag_assignments a
        JOIN customers c ON c.id = a.customer_id
        WHERE a.category_id = ? AND a.tag_value_id = ? AND a.is_active = 1
        ORDER BY c.id
        """, (rs, rowNum) -> new CustomerRef(rs.getLong("id"), rs.getString("phone")),
        sourceCategory.id(), source.id());
    if (targetCategory.selectionMode() == TagSelectionMode.SINGLE
        && sourceCategory.id() != targetCategory.id()) {
      jdbcTemplate.update("""
          UPDATE customer_tag_assignments source_assignment
          JOIN customer_tag_assignments target_assignment
            ON target_assignment.customer_id = source_assignment.customer_id
           AND target_assignment.category_id = ?
           AND target_assignment.is_active = 1
          SET source_assignment.is_active = 0,
              source_assignment.invalidated_reason = ?,
              source_assignment.invalidated_at = NOW(),
              source_assignment.updated_at = NOW()
          WHERE source_assignment.category_id = ?
            AND source_assignment.tag_value_id = ?
            AND source_assignment.is_active = 1
          """, targetCategory.id(), reason, sourceCategory.id(), source.id());
    } else {
      jdbcTemplate.update("""
          UPDATE customer_tag_assignments source_assignment
          JOIN customer_tag_assignments target_assignment
            ON target_assignment.customer_id = source_assignment.customer_id
           AND target_assignment.tag_value_id = ?
           AND target_assignment.is_active = 1
          SET source_assignment.is_active = 0,
              source_assignment.invalidated_reason = ?,
              source_assignment.invalidated_at = NOW(),
              source_assignment.updated_at = NOW()
          WHERE source_assignment.category_id = ?
            AND source_assignment.tag_value_id = ?
            AND source_assignment.is_active = 1
          """, target.id(), reason, sourceCategory.id(), source.id());
    }
    jdbcTemplate.update("""
        UPDATE customer_tag_assignments
        SET category_id = ?, tag_value_id = ?, selection_mode = ?, updated_at = NOW()
        WHERE category_id = ? AND tag_value_id = ?
        """, targetCategory.id(), target.id(), targetCategory.selectionMode().name(), sourceCategory.id(), source.id());
    jdbcTemplate.update("""
        UPDATE tag_analysis_results
        SET category_id = ?, tag_value_id = ?
        WHERE category_id = ? AND tag_value_id = ?
        """, targetCategory.id(), target.id(), sourceCategory.id(), source.id());
    jdbcTemplate.update("""
        UPDATE tag_legacy_value_mappings
        SET category_id = ?, tag_value_id = ?, updated_at = NOW()
        WHERE category_id = ? AND tag_value_id = ?
        """, targetCategory.id(), target.id(), sourceCategory.id(), source.id());
    jdbcTemplate.update("""
        UPDATE unmatched_legacy_tag_values
        SET category_id = ?, mapped_tag_value_id = ?, updated_at = NOW()
        WHERE category_id = ? AND mapped_tag_value_id = ?
        """, targetCategory.id(), target.id(), sourceCategory.id(), source.id());
    jdbcTemplate.update("""
        UPDATE system_tag_suggestions
        SET tag_value_id = ?, tag_name = ?
        WHERE tag_value_id = ?
        """, target.id(), target.displayName(), source.id());
    jdbcTemplate.update(
        "UPDATE personality_tags SET canonical_tag_value_id = ?, updated_at = NOW() WHERE canonical_tag_value_id = ?",
        target.id(),
        source.id());
    synchronizeLegacyFields(affectedCustomers, sourceCategory, targetCategory);
    return affectedCustomers.stream()
        .map(CustomerRef::phone)
        .filter(phone -> phone != null && !phone.isBlank())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  public void mergeCategoryOnlyReferences(TagCategory source, TagCategory target) {
    jdbcTemplate.update("""
        UPDATE tag_analysis_results
        SET category_id = ?
        WHERE category_id = ? AND tag_value_id IS NULL
        """, target.id(), source.id());
    jdbcTemplate.update("""
        UPDATE tag_legacy_value_mappings
        SET category_id = ?, updated_at = NOW()
        WHERE category_id = ? AND tag_value_id IS NULL
        """, target.id(), source.id());
    jdbcTemplate.update("""
        UPDATE unmatched_legacy_tag_values
        SET category_id = ?, updated_at = NOW()
        WHERE category_id = ? AND mapped_tag_value_id IS NULL
        """, target.id(), source.id());
    jdbcTemplate.update("""
        UPDATE customer_tag_category_locks target_lock
        JOIN customer_tag_category_locks source_lock
          ON source_lock.customer_id = target_lock.customer_id
         AND source_lock.category_id = ?
        SET target_lock.locked_by = CASE
              WHEN target_lock.is_locked = 1 THEN target_lock.locked_by
              WHEN source_lock.is_locked = 1 THEN source_lock.locked_by
              ELSE target_lock.locked_by
            END,
            target_lock.lock_reason = CASE
              WHEN target_lock.is_locked = 1 THEN target_lock.lock_reason
              WHEN source_lock.is_locked = 1 THEN source_lock.lock_reason
              ELSE target_lock.lock_reason
            END,
            target_lock.locked_at = CASE
              WHEN target_lock.is_locked = 1 THEN target_lock.locked_at
              WHEN source_lock.is_locked = 1 THEN source_lock.locked_at
              ELSE target_lock.locked_at
            END,
            target_lock.unlocked_by = CASE
              WHEN target_lock.is_locked = 1 OR source_lock.is_locked = 1 THEN NULL
              WHEN source_lock.unlocked_at IS NOT NULL
                AND (target_lock.unlocked_at IS NULL OR source_lock.unlocked_at > target_lock.unlocked_at)
                THEN source_lock.unlocked_by
              ELSE target_lock.unlocked_by
            END,
            target_lock.unlocked_at = CASE
              WHEN target_lock.is_locked = 1 OR source_lock.is_locked = 1 THEN NULL
              WHEN source_lock.unlocked_at IS NOT NULL
                AND (target_lock.unlocked_at IS NULL OR source_lock.unlocked_at > target_lock.unlocked_at)
                THEN source_lock.unlocked_at
              ELSE target_lock.unlocked_at
            END,
            target_lock.is_locked = CASE WHEN target_lock.is_locked = 1 OR source_lock.is_locked = 1 THEN 1 ELSE 0 END,
            target_lock.version = target_lock.version + 1,
            target_lock.updated_at = NOW()
        WHERE target_lock.category_id = ?
        """, source.id(), target.id());
    jdbcTemplate.update("""
        DELETE source_lock
        FROM customer_tag_category_locks source_lock
        JOIN customer_tag_category_locks target_lock
          ON target_lock.customer_id = source_lock.customer_id
         AND target_lock.category_id = ?
        WHERE source_lock.category_id = ?
        """, target.id(), source.id());
    jdbcTemplate.update("""
        UPDATE customer_tag_category_locks
        SET category_id = ?, version = version + 1, updated_at = NOW()
        WHERE category_id = ?
        """, target.id(), source.id());
  }

  public void markValueMerged(long sourceId, long targetId) {
    jdbcTemplate.update("""
        UPDATE tag_values
        SET is_enabled = 0,
            system_selectable = 0,
            manual_selectable = 0,
            merged_into_id = ?,
            version = version + 1,
            updated_at = NOW()
        WHERE id = ?
        """, targetId, sourceId);
  }

  public void ensureTargetAvailability(TagValue source, TagValue target) {
    if (!source.isEnabled() || target.isEnabled()) {
      return;
    }
    jdbcTemplate.update("""
        UPDATE tag_values
        SET is_enabled = 1, version = version + 1, updated_at = NOW()
        WHERE id = ?
        """, target.id());
  }

  public void markCategoryMerged(long sourceId, long targetId) {
    jdbcTemplate.update("""
        UPDATE tag_categories
        SET is_enabled = 0,
            system_inference_enabled = 0,
            manual_edit_enabled = 0,
            merged_into_id = ?,
            version = version + 1,
            updated_at = NOW()
        WHERE id = ?
        """, targetId, sourceId);
  }

  public void saveLegacyMappings(TagValue source, TagCategory sourceCategory, TagValue target, TagCategory targetCategory) {
    insertMapping("TAG_VALUE_MERGE_CODE", sourceCategory.categoryKey(), source.tagValue(), targetCategory.id(), target.id());
    insertMapping("TAG_VALUE_MERGE_NAME", sourceCategory.categoryKey(), source.displayName(), targetCategory.id(), target.id());
    Set<String> synonyms = new LinkedHashSet<>(source.synonyms());
    synonyms.remove(source.tagValue());
    synonyms.remove(source.displayName());
    for (String synonym : synonyms) {
      insertMapping("TAG_VALUE_MERGE_SYNONYM", sourceCategory.categoryKey(), synonym, targetCategory.id(), target.id());
    }
  }

  public void saveCategoryMapping(TagCategory source, TagCategory target) {
    insertMapping("TAG_CATEGORY_MERGE", source.categoryKey(), source.categoryKey(), target.id(), null);
  }

  public void recordOperation(
      String entityType,
      long sourceId,
      long targetId,
      String sourceCode,
      String targetCode,
      TagImpact impact,
      String detailJson,
      String operator) {
    jdbcTemplate.update("""
        INSERT INTO tag_merge_operations (
          entity_type, source_id, target_id, source_code, target_code,
          affected_customers, affected_rules, affected_history, detail_json, operated_by
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        entityType,
        sourceId,
        targetId,
        sourceCode,
        targetCode,
        impact.customerCount(),
        impact.ruleCount(),
        impact.historyCount(),
        detailJson,
        operator);
  }

  private void insertMapping(
      String sourceType,
      String legacyCategoryKey,
      String legacyValue,
      long categoryId,
      Long tagValueId) {
    if (legacyValue == null || legacyValue.isBlank()) {
      return;
    }
    jdbcTemplate.update("""
        INSERT INTO tag_legacy_value_mappings (
          source_type, legacy_category_key, legacy_value, category_id,
          tag_value_id, mapping_status, mapping_note
        ) VALUES (?, ?, ?, ?, ?, 'MAPPED', '标签管理合并自动保留的旧编号映射')
        ON DUPLICATE KEY UPDATE
          category_id = VALUES(category_id),
          tag_value_id = VALUES(tag_value_id),
          mapping_status = 'MAPPED',
          mapping_note = VALUES(mapping_note),
          updated_at = NOW()
        """, sourceType, legacyCategoryKey == null ? "" : legacyCategoryKey, legacyValue.trim(), categoryId, tagValueId);
  }

  private void synchronizeLegacyFields(
      List<CustomerRef> customers,
      TagCategory sourceCategory,
      TagCategory targetCategory) {
    String sourceColumn = legacyColumn(sourceCategory.boundField());
    String targetColumn = legacyColumn(targetCategory.boundField());
    for (CustomerRef customer : customers) {
      String targetValue = targetColumn == null ? null : activeCodes(customer.id(), targetCategory.id());
      if (targetColumn != null && sourceColumn != null && !targetColumn.equals(sourceColumn)) {
        jdbcTemplate.update(
            "UPDATE customers SET " + targetColumn + " = ?, " + sourceColumn
                + " = NULL, version = version + 1, updated_at = NOW() WHERE id = ?",
            blankToNull(targetValue),
            customer.id());
      } else if (targetColumn != null) {
        jdbcTemplate.update(
            "UPDATE customers SET " + targetColumn + " = ?, version = version + 1, updated_at = NOW() WHERE id = ?",
            blankToNull(targetValue),
            customer.id());
      } else {
        jdbcTemplate.update(
            "UPDATE customers SET version = version + 1, updated_at = NOW() WHERE id = ?",
            customer.id());
      }
    }
  }

  private String activeCodes(long customerId, long categoryId) {
    return String.join(",", new LinkedHashSet<>(jdbcTemplate.queryForList("""
        SELECT v.tag_value
        FROM customer_tag_assignments a
        JOIN tag_values v ON v.id = a.tag_value_id AND v.category_id = a.category_id
        WHERE a.customer_id = ? AND a.category_id = ? AND a.is_active = 1
        ORDER BY v.sort_order, v.id
        """, String.class, customerId, categoryId)));
  }

  private String legacyColumn(String boundField) {
    if (boundField == null) {
      return null;
    }
    return switch (boundField) {
      case "personalityType" -> "personality_type";
      case "bodyConcerns" -> "body_concerns";
      case "worries" -> "worries";
      case "intentLevel" -> "intent_level";
      default -> null;
    };
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private record CustomerRef(long id, String phone) {
  }
}
