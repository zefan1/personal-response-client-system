package com.privateflow.modules.tags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LegacyCustomerTagSynchronizer {

  private static final Map<String, String> LEGACY_COLUMNS = Map.of(
      "personalityType", "personality_type",
      "bodyConcerns", "body_concerns",
      "worries", "worries",
      "intentLevel", "intent_level");

  private final JdbcTemplate jdbcTemplate;
  private final TagExchangeService exchangeService;

  @Autowired
  public LegacyCustomerTagSynchronizer(
      JdbcTemplate jdbcTemplate,
      TagExchangeService exchangeService) {
    this.jdbcTemplate = jdbcTemplate;
    this.exchangeService = exchangeService;
  }

  public LegacyCustomerTagSynchronizer(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    this.exchangeService = null;
  }

  @Transactional
  public void synchronize(String phone, Map<String, ?> changedFields) {
    synchronizeLegacyCustomerField(phone, changedFields);
  }

  @Transactional
  public void synchronize(
      String phone,
      Map<String, ?> changedFields,
      TagExchangeSourceType sourceType,
      String sourceRecordId) {
    if (phone == null || phone.isBlank() || changedFields == null || changedFields.isEmpty()) {
      return;
    }
    if (exchangeService == null) {
      throw new IllegalStateException("source-aware tag synchronization requires exchange service");
    }
    TagExchangeResult result = exchangeService.prepareInbound(sourceType, sourceRecordId, changedFields);
    synchronize(phone, result, sourceType, sourceRecordId);
  }

  @Transactional
  public void synchronize(
      String phone,
      TagExchangeResult result,
      TagExchangeSourceType sourceType,
      String sourceRecordId) {
    if (phone == null || phone.isBlank() || result == null) {
      return;
    }
    CustomerRef customer = jdbcTemplate.query("""
        SELECT id, version FROM customers WHERE phone = ? LIMIT 1
        """, (rs, rowNum) -> new CustomerRef(rs.getLong("id"), rs.getInt("version")), phone)
        .stream().findFirst().orElse(null);
    if (customer == null) {
      return;
    }
    for (Map.Entry<String, Object> entry : result.acceptedFields().entrySet()) {
      String column = LEGACY_COLUMNS.get(entry.getKey());
      if (column != null) {
        synchronizeField(customer, entry.getKey(), column, entry.getValue());
      }
    }
    result.unmatched().forEach(item -> recordExchangeUnmatched(customer.id(), item));
  }

  private void synchronizeLegacyCustomerField(String phone, Map<String, ?> changedFields) {
    if (phone == null || phone.isBlank() || changedFields == null || changedFields.isEmpty()) {
      return;
    }
    CustomerRef customer = jdbcTemplate.query("""
        SELECT id, version FROM customers WHERE phone = ? LIMIT 1
        """, (rs, rowNum) -> new CustomerRef(rs.getLong("id"), rs.getInt("version")), phone)
        .stream().findFirst().orElse(null);
    if (customer == null) {
      return;
    }
    for (Map.Entry<String, ?> entry : changedFields.entrySet()) {
      String column = LEGACY_COLUMNS.get(entry.getKey());
      if (column == null) {
        continue;
      }
      synchronizeField(customer, entry.getKey(), column, entry.getValue());
    }
  }

  private void recordExchangeUnmatched(long customerId, TagExchangeUnmatchedValue item) {
    jdbcTemplate.update("""
        UPDATE unmatched_legacy_tag_values
        SET status = 'SUPERSEDED', updated_at = NOW()
        WHERE customer_id = ? AND source_type = ? AND legacy_field = ? AND status = 'PENDING'
        """, customerId, item.sourceType().name(), item.boundField());
    Long numericSourceRecordId = parseLong(item.sourceRecordId());
    String resolutionNote = numericSourceRecordId == null && item.sourceRecordId() != null
        ? "sourceRecordId=" + item.sourceRecordId()
        : null;
    jdbcTemplate.update("""
        INSERT INTO unmatched_legacy_tag_values (
          customer_id, source_type, source_record_id, legacy_field, raw_value,
          raw_value_hash, category_id, status, resolution_note
        ) VALUES (?, ?, ?, ?, ?, SHA2(?, 256), ?, 'PENDING', ?)
        ON DUPLICATE KEY UPDATE
          status = 'PENDING',
          source_type = VALUES(source_type),
          source_record_id = VALUES(source_record_id),
          category_id = VALUES(category_id),
          resolution_note = VALUES(resolution_note),
          updated_at = NOW()
        """,
        customerId,
        item.sourceType().name(),
        numericSourceRecordId,
        item.boundField(),
        item.rawValue(),
        item.rawValue(),
        item.categoryId(),
        resolutionNote);
  }

  private Long parseLong(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private void synchronizeField(CustomerRef customer, String boundField, String column, Object value) {
    CategoryRef category = jdbcTemplate.query("""
        SELECT id, selection_mode, is_enabled
        FROM tag_categories
        WHERE bound_field = ? AND merged_into_id IS NULL
        LIMIT 1
        """, (rs, rowNum) -> new CategoryRef(
            rs.getLong("id"),
            TagSelectionMode.valueOf(rs.getString("selection_mode")),
            rs.getInt("is_enabled") == 1), boundField).stream().findFirst().orElse(null);
    if (category == null) {
      return;
    }

    String rawValue = value == null ? "" : String.valueOf(value).trim();
    List<String> tokens = tokens(rawValue, category.selectionMode());
    List<ResolvedToken> resolved = new ArrayList<>();
    boolean allMatched = !tokens.isEmpty();
    for (String token : tokens) {
      List<ValueRef> matches = jdbcTemplate.query("""
          SELECT id, tag_value, is_enabled
          FROM tag_values
          WHERE category_id = ? AND merged_into_id IS NULL
            AND (tag_value = ? OR display_name = ?)
          ORDER BY id
          """, (rs, rowNum) -> new ValueRef(
              rs.getLong("id"),
              rs.getString("tag_value"),
              rs.getInt("is_enabled") == 1), category.id(), token, token);
      if (matches.size() != 1) {
        allMatched = false;
        continue;
      }
      resolved.add(new ResolvedToken(token, matches.get(0)));
    }

    LinkedHashMap<Long, ValueRef> distinctValues = new LinkedHashMap<>();
    resolved.forEach(match -> distinctValues.putIfAbsent(match.value().id(), match.value()));
    Set<Long> desiredActive = new LinkedHashSet<>();
    if (category.enabled()) {
      distinctValues.values().stream().filter(ValueRef::enabled).map(ValueRef::id).forEach(desiredActive::add);
    }
    Set<Long> currentLegacyActive = new LinkedHashSet<>(jdbcTemplate.queryForList("""
        SELECT tag_value_id FROM customer_tag_assignments
        WHERE customer_id = ? AND category_id = ? AND is_active = 1
          AND source_type IN ('LEGACY_MIGRATION', 'LEGACY_FIELD_SYNC')
        ORDER BY id
        """, Long.class, customer.id(), category.id()));

    if (!currentLegacyActive.equals(desiredActive)) {
      jdbcTemplate.update("""
          UPDATE customer_tag_assignments
          SET is_active = 0, invalidated_reason = 'LEGACY_FIELD_CHANGED', invalidated_at = NOW(), updated_at = NOW()
          WHERE customer_id = ? AND category_id = ? AND is_active = 1
            AND source_type IN ('LEGACY_MIGRATION', 'LEGACY_FIELD_SYNC')
          """, customer.id(), category.id());
      boolean singleCategoryOccupied = category.selectionMode() == TagSelectionMode.SINGLE
          && activeCategoryAssignmentExists(customer.id(), category.id());
      for (ValueRef tagValue : distinctValues.values()) {
        boolean active = category.enabled() && tagValue.enabled();
        if (active && activeAssignmentExists(customer.id(), category.id(), tagValue.id())) {
          continue;
        }
        if (active && singleCategoryOccupied) {
          continue;
        }
        if (!active && inactiveDisabledAssignmentExists(customer.id(), category.id(), tagValue.id())) {
          continue;
        }
        jdbcTemplate.update("""
            INSERT INTO customer_tag_assignments (
              customer_id, category_id, tag_value_id, selection_mode, is_active, source_type,
              evidence_text, operator_account, customer_version, invalidated_reason, invalidated_at
            ) VALUES (?, ?, ?, ?, ?, 'LEGACY_FIELD_SYNC', ?, 'SYSTEM_LEGACY_BRIDGE', ?, ?, ?)
            """,
            customer.id(), category.id(), tagValue.id(), category.selectionMode().name(), active ? 1 : 0,
            "历史兼容字段 " + boundField + " 原文：" + rawValue,
            customer.version(), active ? null : "LEGACY_TAG_DISABLED", active ? null : java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
      }
    }

    updateUnmatchedHistory(customer.id(), category.id(), boundField, rawValue, allMatched);
    if (allMatched) {
      String normalized = normalizedCurrentValue(customer.id(), category.id(), resolved);
      if (!normalized.isBlank() && !normalized.equals(rawValue)) {
        jdbcTemplate.update("UPDATE customers SET " + column + " = ?, updated_at = NOW() WHERE id = ?", normalized, customer.id());
      }
    }
  }

  private boolean activeAssignmentExists(long customerId, long categoryId, long tagValueId) {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM customer_tag_assignments
        WHERE customer_id = ? AND category_id = ? AND tag_value_id = ? AND is_active = 1
        """, Integer.class, customerId, categoryId, tagValueId);
    return count != null && count > 0;
  }

  private boolean activeCategoryAssignmentExists(long customerId, long categoryId) {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM customer_tag_assignments
        WHERE customer_id = ? AND category_id = ? AND is_active = 1
        """, Integer.class, customerId, categoryId);
    return count != null && count > 0;
  }

  private boolean inactiveDisabledAssignmentExists(long customerId, long categoryId, long tagValueId) {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM customer_tag_assignments
        WHERE customer_id = ? AND category_id = ? AND tag_value_id = ? AND is_active = 0
          AND source_type IN ('LEGACY_MIGRATION', 'LEGACY_FIELD_SYNC')
          AND invalidated_reason = 'LEGACY_TAG_DISABLED'
        """, Integer.class, customerId, categoryId, tagValueId);
    return count != null && count > 0;
  }

  private void updateUnmatchedHistory(long customerId, long categoryId, String boundField, String rawValue, boolean allMatched) {
    jdbcTemplate.update("""
        UPDATE unmatched_legacy_tag_values
        SET status = 'SUPERSEDED', updated_at = NOW()
        WHERE customer_id = ? AND source_type = 'CUSTOMER_FIELD' AND legacy_field = ? AND status = 'PENDING'
        """, customerId, boundField);
    if (rawValue.isBlank() || allMatched) {
      return;
    }
    jdbcTemplate.update("""
        INSERT INTO unmatched_legacy_tag_values (
          customer_id, source_type, source_record_id, legacy_field, raw_value, raw_value_hash, category_id, status
        ) VALUES (?, 'CUSTOMER_FIELD', ?, ?, ?, SHA2(?, 256), ?, 'PENDING')
        ON DUPLICATE KEY UPDATE status = 'PENDING', category_id = VALUES(category_id), updated_at = NOW()
        """, customerId, customerId, boundField, rawValue, rawValue, categoryId);
  }

  private String normalizedCurrentValue(long customerId, long categoryId, List<ResolvedToken> resolved) {
    List<String> activeCodes = jdbcTemplate.queryForList("""
        SELECT v.tag_value
        FROM customer_tag_assignments a
        JOIN tag_values v ON v.id = a.tag_value_id AND v.category_id = a.category_id
        WHERE a.customer_id = ? AND a.category_id = ? AND a.is_active = 1
        ORDER BY v.sort_order, v.id
        """, String.class, customerId, categoryId);
    if (!activeCodes.isEmpty()) {
      return String.join(",", new LinkedHashSet<>(activeCodes));
    }
    LinkedHashSet<String> matchedCodes = new LinkedHashSet<>();
    resolved.forEach(token -> matchedCodes.add(token.value().code()));
    return String.join(",", matchedCodes);
  }

  private List<String> tokens(String rawValue, TagSelectionMode selectionMode) {
    if (rawValue == null || rawValue.isBlank()) {
      return List.of();
    }
    if (selectionMode == TagSelectionMode.SINGLE) {
      return List.of(rawValue.trim());
    }
    String normalized = rawValue
        .replace('，', ',')
        .replace('、', ',')
        .replace('；', ',')
        .replace(';', ',')
        .replace('|', ',')
        .replace('\r', ',')
        .replace('\n', ',')
        .replace('\t', ',');
    return Arrays.stream(normalized.split(","))
        .map(String::trim)
        .filter(token -> !token.isBlank())
        .toList();
  }

  private record CustomerRef(long id, int version) {
  }

  private record CategoryRef(long id, TagSelectionMode selectionMode, boolean enabled) {
  }

  private record ValueRef(long id, String code, boolean enabled) {
  }

  private record ResolvedToken(String raw, ValueRef value) {
  }
}
