package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.ws.WsPushService;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TagFlywayMariaDbIntegrationTest {

  @Test
  @EnabledIfEnvironmentVariable(named = "TAG_FLYWAY_IT", matches = "true")
  void migratesFreshMariaDbAndSecondRunIsNoOp() throws Exception {
    String url = required("TAG_FLYWAY_URL");
    String username = required("TAG_FLYWAY_USERNAME");
    String password = System.getenv().getOrDefault("TAG_FLYWAY_PASSWORD", "");
    Flyway flyway = Flyway.configure()
        .dataSource(url, username, password)
        .locations("classpath:db/migration")
        .load();

    MigrateResult first = flyway.migrate();
    MigrateResult second = flyway.migrate();

    assertThat(first.targetSchemaVersion).isEqualTo("69");
    assertThat(first.migrationsExecuted).isGreaterThan(0);
    assertThat(second.migrationsExecuted).isZero();
    try (var connection = DriverManager.getConnection(url, username, password);
         var statement = connection.createStatement()) {
      try (var result = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE version='69' AND success=1")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(1);
      }
      try (var result = statement.executeQuery("SELECT COUNT(*) FROM tag_values WHERE meaning='' OR synonyms_json='[]'")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isZero();
      }
      try (var result = statement.executeQuery("SELECT COUNT(*) FROM personality_tags WHERE enabled=0 AND migration_status='MAPPED' AND canonical_tag_value_id IS NOT NULL")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(3);
      }
      try (var result = statement.executeQuery("SELECT COUNT(*) FROM tag_legacy_value_mappings WHERE source_type='PERSONALITY_TAGS' AND mapping_status='MAPPED'")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(3);
      }
      try (var result = statement.executeQuery("""
          SELECT COUNT(*)
          FROM accounts a
          WHERE a.role='ADMIN'
            AND NOT EXISTS (
              SELECT 1
              FROM account_permissions p
              WHERE p.account_id=a.id
                AND p.permission_code='TAG_MANAGEMENT'
                AND p.is_enabled=1
            )
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isZero();
      }
      try (var result = statement.executeQuery("""
          SELECT COUNT(*)
          FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='tag_merge_operations'
            AND COLUMN_NAME IN (
              'entity_type', 'source_id', 'target_id', 'source_code', 'target_code',
              'affected_customers', 'affected_rules', 'affected_history',
              'detail_json', 'operated_by', 'created_at'
            )
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(11);
      }
      try (var result = statement.executeQuery("""
          SELECT COUNT(*)
          FROM information_schema.KEY_COLUMN_USAGE
          WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='tag_values'
            AND CONSTRAINT_NAME='fk_tag_values_merged_into'
            AND COLUMN_NAME='merged_into_id'
            AND REFERENCED_TABLE_NAME='tag_values'
            AND REFERENCED_COLUMN_NAME='id'
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(1);
      }
      try (var result = statement.executeQuery("""
          SELECT COUNT(*)
          FROM information_schema.KEY_COLUMN_USAGE
          WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='tag_values'
            AND CONSTRAINT_NAME='fk_tag_values_merged_into'
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(1);
      }
    }

    DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.update("INSERT INTO customers (phone, body_concerns) VALUES ('19900006888', '腹直肌分离,漏尿')");
    LegacyCustomerTagSynchronizer synchronizer = new LegacyCustomerTagSynchronizer(jdbcTemplate);
    synchronizer.synchronize("19900006888", Map.of("bodyConcerns", "腹直肌分离,漏尿"));
    assertThat(jdbcTemplate.queryForObject(
        "SELECT body_concerns FROM customers WHERE phone='19900006888'", String.class))
        .isEqualTo("DIASTASIS_RECTI,URINE_LEAKAGE");
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM customer_tag_assignments a
        JOIN customers c ON c.id=a.customer_id
        WHERE c.phone='19900006888' AND a.is_active=1
        """, Integer.class)).isEqualTo(2);

    jdbcTemplate.update("UPDATE customers SET body_concerns='腹直肌分离,未知关注' WHERE phone='19900006888'");
    synchronizer.synchronize("19900006888", Map.of("bodyConcerns", "腹直肌分离,未知关注"));
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM unmatched_legacy_tag_values u
        JOIN customers c ON c.id=u.customer_id
        WHERE c.phone='19900006888' AND u.legacy_field='bodyConcerns' AND u.status='PENDING'
        """, Integer.class)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM customer_tag_assignments a
        JOIN customers c ON c.id=a.customer_id
        WHERE c.phone='19900006888' AND a.is_active=1
        """, Integer.class)).isEqualTo(1);

    TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
      jdbcTemplate.update("UPDATE customers SET body_concerns='漏尿' WHERE phone='19900006888'");
      synchronizer.synchronize("19900006888", Map.of("bodyConcerns", "漏尿"));
      throw new IllegalStateException("force rollback");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT body_concerns FROM customers WHERE phone='19900006888'", String.class))
        .isEqualTo("腹直肌分离,未知关注");
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM customer_tag_assignments a
        JOIN customers c ON c.id=a.customer_id
        JOIN tag_values v ON v.id=a.tag_value_id
        WHERE c.phone='19900006888' AND a.is_active=1 AND v.tag_value='DIASTASIS_RECTI'
        """, Integer.class)).isEqualTo(1);

    jdbcTemplate.update("INSERT INTO customers (phone) VALUES ('19900006999')");
    Long singleCategoryId = jdbcTemplate.queryForObject(
        "SELECT id FROM tag_categories WHERE category_key='intent_level'", Long.class);
    Long sourceValueId = jdbcTemplate.queryForObject(
        "SELECT id FROM tag_values WHERE category_id=? AND tag_value='HIGH'", Long.class, singleCategoryId);
    Long targetValueId = jdbcTemplate.queryForObject(
        "SELECT id FROM tag_values WHERE category_id=? AND tag_value='MEDIUM'", Long.class, singleCategoryId);
    jdbcTemplate.update("""
        INSERT INTO customer_tag_assignments (
          customer_id, category_id, tag_value_id, selection_mode, is_active, source_type
        )
        SELECT id, ?, ?, 'SINGLE', 1, 'MANUAL'
        FROM customers
        WHERE phone='19900006999'
        """, singleCategoryId, sourceValueId);
    ObjectMapper objectMapper = new ObjectMapper();
    TagRepository tagRepository = new TagRepository(jdbcTemplate, objectMapper);
    TagCategory singleCategory = tagRepository.findCategory(singleCategoryId).orElseThrow();
    TagValue sourceValue = tagRepository.findValue(sourceValueId).orElseThrow();
    TagValue targetValue = tagRepository.findValue(targetValueId).orElseThrow();
    new TagMergeRepository(jdbcTemplate).mergeValueReferences(
        sourceValue,
        singleCategory,
        targetValue,
        singleCategory,
        "integration test value merge");
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM customer_tag_assignments a
        JOIN customers c ON c.id=a.customer_id
        WHERE c.phone='19900006999'
          AND a.category_id=?
          AND a.tag_value_id=?
          AND a.is_active=1
        """, Integer.class, singleCategoryId, targetValueId)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT intent_level FROM customers WHERE phone='19900006999'", String.class))
        .isEqualTo("MEDIUM");
    assertThat(jdbcTemplate.queryForObject(
        "SELECT version FROM customers WHERE phone='19900006999'", Integer.class))
        .isEqualTo(1);

    jdbcTemplate.update("""
        INSERT INTO tag_categories (
          category_key, category_name, purpose, bound_field, selection_mode,
          is_builtin, is_enabled, sort_order
        ) VALUES ('clearable_category', 'Clearable category', 'purpose to clear', NULL, 'SINGLE', 0, 1, 900)
        """);
    Long clearableCategoryId = jdbcTemplate.queryForObject(
        "SELECT id FROM tag_categories WHERE category_key='clearable_category'", Long.class);
    TagCategory clearableCategory = tagRepository.findCategory(clearableCategoryId).orElseThrow();
    assertThat(tagRepository.updateCategory(
        clearableCategoryId,
        categoryPurposeUpdate("", null))).isZero();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT purpose FROM tag_categories WHERE id=?", String.class, clearableCategoryId))
        .isEqualTo("purpose to clear");
    assertThat(tagRepository.updateCategory(
        clearableCategoryId,
        categoryPurposeUpdate("", clearableCategory.version()))).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT purpose FROM tag_categories WHERE id=?", String.class, clearableCategoryId))
        .isEmpty();

    jdbcTemplate.update("""
        INSERT INTO tag_values (
          category_id, tag_value, display_name, meaning, applicable_when,
          not_applicable_when, positive_examples, negative_examples, synonyms_json,
          system_selectable, manual_selectable, is_enabled, sort_order
        ) VALUES (?, 'CLEARABLE', 'Clearable value', 'meaning', 'applicable',
                  'not applicable', 'positive', 'negative', '["alias"]', 1, 1, 1, 1)
        """, clearableCategoryId);
    Long clearableValueId = jdbcTemplate.queryForObject(
        "SELECT id FROM tag_values WHERE category_id=? AND tag_value='CLEARABLE'",
        Long.class,
        clearableCategoryId);
    TagValue clearableValue = tagRepository.findValue(clearableValueId).orElseThrow();
    assertThat(tagRepository.updateValue(clearableValueId, valueTextUpdate(null))).isZero();
    assertThat(tagRepository.updateValue(
        clearableValueId,
        valueTextUpdate(clearableValue.version()))).isEqualTo(1);
    Map<String, Object> clearedValue = jdbcTemplate.queryForMap("""
        SELECT meaning, applicable_when, not_applicable_when,
               positive_examples, negative_examples, synonyms_json
        FROM tag_values WHERE id = ?
        """, clearableValueId);
    assertThat(clearedValue).containsEntry("meaning", "")
        .containsEntry("applicable_when", "")
        .containsEntry("not_applicable_when", "")
        .containsEntry("positive_examples", "")
        .containsEntry("negative_examples", "")
        .containsEntry("synonyms_json", "[]");

    Integer targetVersion = jdbcTemplate.queryForObject(
        "SELECT version FROM tag_values WHERE id=?", Integer.class, targetValueId);
    assertThat(tagRepository.toggleValue(targetValueId, false, targetVersion)).isEqualTo(1);
    Long intentCustomerId = jdbcTemplate.queryForObject(
        "SELECT id FROM customers WHERE phone='19900006999'", Long.class);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT intent_level FROM customers WHERE id=?", String.class, intentCustomerId))
        .isEqualTo("MEDIUM");
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM customer_tag_assignments
        WHERE customer_id=? AND tag_value_id=? AND is_active=1
        """, Integer.class, intentCustomerId, targetValueId)).isEqualTo(1);
    assertThat(new CustomerTagFoundationRepository(jdbcTemplate).findCurrentAssignments(intentCustomerId)).isEmpty();
    assertThat(tagRepository.toggleValue(targetValueId, true, targetVersion + 1)).isEqualTo(1);
    Integer categoryVersion = jdbcTemplate.queryForObject(
        "SELECT version FROM tag_categories WHERE id=?", Integer.class, singleCategoryId);
    assertThat(tagRepository.toggleCategory(singleCategoryId, false, categoryVersion)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT intent_level FROM customers WHERE id=?", String.class, intentCustomerId))
        .isEqualTo("MEDIUM");
    assertThat(new CustomerTagFoundationRepository(jdbcTemplate).findCurrentAssignments(intentCustomerId)).isEmpty();
    assertThat(tagRepository.toggleCategory(singleCategoryId, true, categoryVersion + 1)).isEqualTo(1);

    jdbcTemplate.update("""
        INSERT INTO tag_categories (
          category_key, category_name, purpose, bound_field, selection_mode,
          is_builtin, is_enabled, sort_order
        ) VALUES ('custom_personality', 'Custom personality', '', NULL, 'SINGLE', 0, 1, 901)
        """);
    Long personalitySourceId = jdbcTemplate.queryForObject(
        "SELECT id FROM tag_categories WHERE category_key='personality_type'", Long.class);
    Long personalityTargetId = jdbcTemplate.queryForObject(
        "SELECT id FROM tag_categories WHERE category_key='custom_personality'", Long.class);
    jdbcTemplate.update("INSERT INTO customers (phone, personality_type) VALUES ('19900006777', 'LOYALIST')");
    synchronizer.synchronize("19900006777", Map.of("personalityType", "LOYALIST"));
    Long personalityCustomerId = jdbcTemplate.queryForObject(
        "SELECT id FROM customers WHERE phone='19900006777'", Long.class);
    jdbcTemplate.update("""
        INSERT INTO customer_tag_category_locks (
          customer_id, category_id, is_locked, locked_by, lock_reason,
          locked_at, unlocked_by, unlocked_at
        ) VALUES (?, ?, 1, 'source-locker', 'source-reason',
                  '2026-07-14 10:00:00', NULL, NULL)
        """, personalityCustomerId, personalitySourceId);
    jdbcTemplate.update("""
        INSERT INTO customer_tag_category_locks (
          customer_id, category_id, is_locked, locked_by, lock_reason,
          locked_at, unlocked_by, unlocked_at
        ) VALUES (?, ?, 0, 'target-locker', 'target-old-reason',
                  '2026-07-13 10:00:00', 'target-unlocker', '2026-07-13 11:00:00')
        """, personalityCustomerId, personalityTargetId);
    jdbcTemplate.update("""
        INSERT INTO followup_rules (
          name, condition_json, action_type, action_config, priority, enabled, is_builtin
        ) VALUES (
          'tag category merge integration',
          '{"field":"personalityType","value":"LOYALIST","categoryKey":"personality_type"}',
          'ALERT', '{}', 1, 1, 0
        )
        """);

    TagCategory personalitySource = tagRepository.findCategory(personalitySourceId).orElseThrow();
    TagCategory personalityTarget = tagRepository.findCategory(personalityTargetId).orElseThrow();
    TagAdminService tagAdminService = new TagAdminService(
        tagRepository,
        new TagMergeRepository(jdbcTemplate),
        new TagRuleReferenceService(jdbcTemplate, objectMapper),
        mock(ApplicationEventPublisher.class),
        mock(WsPushService.class),
        mock(AuditLogger.class),
        mock(TagConfigProvider.class),
        objectMapper);
    transaction.executeWithoutResult(status -> tagAdminService.mergeCategory(
        personalitySourceId,
        new TagMergeRequest(personalityTargetId, personalitySource.version(), personalityTarget.version())));

    assertThat(jdbcTemplate.queryForObject(
        "SELECT bound_field FROM tag_categories WHERE id=?", String.class, personalityTargetId))
        .isEqualTo("personalityType");
    assertThat(jdbcTemplate.queryForObject(
        "SELECT bound_field FROM tag_categories WHERE id=?", String.class, personalitySourceId))
        .isNull();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT merged_into_id FROM tag_categories WHERE id=?", Long.class, personalitySourceId))
        .isEqualTo(personalityTargetId);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT personality_type FROM customers WHERE id=?", String.class, personalityCustomerId))
        .isEqualTo("LOYALIST");
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM customer_tag_assignments a
        JOIN tag_values v ON v.id=a.tag_value_id AND v.category_id=a.category_id
        WHERE a.customer_id=? AND a.category_id=? AND a.is_active=1 AND v.tag_value='LOYALIST'
        """, Integer.class, personalityCustomerId, personalityTargetId)).isEqualTo(1);
    Map<String, Object> mergedLock = jdbcTemplate.queryForMap("""
        SELECT is_locked, locked_by, lock_reason, unlocked_by, unlocked_at
        FROM customer_tag_category_locks
        WHERE customer_id=? AND category_id=?
        """, personalityCustomerId, personalityTargetId);
    assertThat(((Number) mergedLock.get("is_locked")).intValue()).isEqualTo(1);
    assertThat(mergedLock).containsEntry("locked_by", "source-locker")
        .containsEntry("lock_reason", "source-reason")
        .containsEntry("unlocked_by", null)
        .containsEntry("unlocked_at", null);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM customer_tag_category_locks
        WHERE customer_id=? AND category_id=?
        """, Integer.class, personalityCustomerId, personalitySourceId)).isZero();
    String mergedRule = jdbcTemplate.queryForObject(
        "SELECT condition_json FROM followup_rules WHERE name='tag category merge integration'",
        String.class);
    assertThat(mergedRule).contains("\"field\":\"personalityType\"")
        .contains("\"categoryKey\":\"custom_personality\"")
        .doesNotContain("\"categoryKey\":\"personality_type\"");
  }

  private TagCategoryRequest categoryPurposeUpdate(String purpose, Integer version) {
    return new TagCategoryRequest(
        null, purpose, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, version);
  }

  private TagValueRequest valueTextUpdate(Integer version) {
    return new TagValueRequest(
        null, null, null, "", "", "", "", "", List.of(),
        null, null, null, null, version);
  }

  private String required(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(key + " is required");
    }
    return value;
  }
}
