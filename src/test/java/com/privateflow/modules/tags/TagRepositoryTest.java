package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class TagRepositoryTest {

  private JdbcTemplate jdbcTemplate;
  private TagRepository repository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:tag_repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS personality_tags");
    jdbcTemplate.execute("DROP TABLE IF EXISTS customer_tag_category_locks");
    jdbcTemplate.execute("DROP TABLE IF EXISTS unmatched_legacy_tag_values");
    jdbcTemplate.execute("DROP TABLE IF EXISTS system_tag_suggestions");
    jdbcTemplate.execute("DROP TABLE IF EXISTS tag_legacy_value_mappings");
    jdbcTemplate.execute("DROP TABLE IF EXISTS tag_analysis_results");
    jdbcTemplate.execute("DROP TABLE IF EXISTS customer_tag_assignments");
    jdbcTemplate.execute("DROP TABLE IF EXISTS tag_values");
    jdbcTemplate.execute("DROP TABLE IF EXISTS tag_categories");
    jdbcTemplate.execute("DROP TABLE IF EXISTS customers");
    jdbcTemplate.execute("""
        CREATE TABLE tag_categories (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          category_key VARCHAR(50) NOT NULL,
          category_name VARCHAR(100) NOT NULL,
          purpose VARCHAR(500) NOT NULL DEFAULT '',
          bound_field VARCHAR(50),
          selection_mode VARCHAR(16) NOT NULL DEFAULT 'SINGLE',
          system_inference_enabled TINYINT NOT NULL DEFAULT 0,
          manual_edit_enabled TINYINT NOT NULL DEFAULT 1,
          auto_update_mode VARCHAR(20) NOT NULL DEFAULT 'RECORD_ONLY',
          min_confidence DECIMAL(5,4) NOT NULL DEFAULT 0.8500,
          min_evidence_messages INT NOT NULL DEFAULT 1,
          cooldown_hours INT NOT NULL DEFAULT 0,
          uncertain_policy VARCHAR(20) NOT NULL DEFAULT 'KEEP_CURRENT',
          use_for_reply TINYINT NOT NULL DEFAULT 1,
          use_for_filter TINYINT NOT NULL DEFAULT 1,
          use_for_statistics TINYINT NOT NULL DEFAULT 1,
          use_for_followup_rules TINYINT NOT NULL DEFAULT 1,
          is_builtin TINYINT NOT NULL DEFAULT 0,
          is_enabled TINYINT NOT NULL DEFAULT 1,
          sort_order INT NOT NULL DEFAULT 0,
          merged_into_id BIGINT,
          version INT NOT NULL DEFAULT 0,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE tag_values (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          category_id BIGINT NOT NULL,
          tag_value VARCHAR(50) NOT NULL,
          display_name VARCHAR(100) NOT NULL,
          meaning VARCHAR(500) NOT NULL DEFAULT '',
          applicable_when VARCHAR(1000) NOT NULL DEFAULT '',
          not_applicable_when VARCHAR(1000) NOT NULL DEFAULT '',
          positive_examples VARCHAR(1000) NOT NULL DEFAULT '',
          negative_examples VARCHAR(1000) NOT NULL DEFAULT '',
          synonyms_json VARCHAR(2000) NOT NULL DEFAULT '[]',
          system_selectable TINYINT NOT NULL DEFAULT 0,
          manual_selectable TINYINT NOT NULL DEFAULT 1,
          is_enabled TINYINT NOT NULL DEFAULT 1,
          sort_order INT NOT NULL DEFAULT 0,
          merged_into_id BIGINT,
          version INT NOT NULL DEFAULT 0,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    createImpactTables();
    repository = new TagRepository(jdbcTemplate, new ObjectMapper());
  }

  @Test
  void mapsCompleteCategoryAndValueMetadata() {
    jdbcTemplate.update("""
        INSERT INTO tag_categories (
          id, category_key, category_name, purpose, bound_field, selection_mode,
          system_inference_enabled, manual_edit_enabled, auto_update_mode,
          min_confidence, min_evidence_messages, cooldown_hours, uncertain_policy,
          use_for_reply, use_for_filter, use_for_statistics, use_for_followup_rules,
          is_builtin, is_enabled, sort_order
        ) VALUES (1, 'intent_level', '意向等级', '跟进优先级', 'intentLevel', 'SINGLE',
                  1, 1, 'REPLACE', 0.9000, 2, 24, 'KEEP_CURRENT', 1, 1, 1, 1, 1, 1, 1)
        """);
    jdbcTemplate.update("""
        INSERT INTO tag_values (
          id, category_id, tag_value, display_name, meaning, applicable_when,
          not_applicable_when, positive_examples, negative_examples, synonyms_json,
          system_selectable, manual_selectable, is_enabled, sort_order
        ) VALUES
          (1, 1, 'HIGH', '高意向', '近期行动信号', '明确预约', '只问价格', '确认周六到店', '只问一次价格', '["高意向","优先跟进"]', 1, 1, 1, 1),
          (2, 1, 'CLOSED', '已成交', '真实成交', '订单成功', '仅口头考虑', '支付成功', '考虑购买', '["已成交"]', 0, 1, 1, 2)
        """);

    TagCategory category = repository.listTree().get(0);
    assertThat(category.purpose()).isEqualTo("跟进优先级");
    assertThat(category.selectionMode()).isEqualTo(TagSelectionMode.SINGLE);
    assertThat(category.autoUpdateMode()).isEqualTo(TagAutoUpdateMode.REPLACE);
    assertThat(category.minConfidence()).isEqualByComparingTo("0.9000");
    assertThat(category.values()).hasSize(2);
    assertThat(category.values().get(0).synonyms()).containsExactly("高意向", "优先跟进");

    assertThat(category.values().get(1).systemSelectable()).isFalse();
  }

  @Test
  void listTreeLoadsAllCategoriesAndValuesWithExactlyTwoQueries() {
    jdbcTemplate.update("""
        INSERT INTO tag_categories (id, category_key, category_name, sort_order) VALUES
          (1, 'first', '第一分类', 1),
          (2, 'second', '第二分类', 2),
          (3, 'third', '第三分类', 3)
        """);
    jdbcTemplate.update("""
        INSERT INTO tag_values (id, category_id, tag_value, display_name, sort_order) VALUES
          (11, 1, 'FIRST_B', '第一分类 B', 2),
          (12, 1, 'FIRST_A', '第一分类 A', 1),
          (21, 2, 'SECOND_A', '第二分类 A', 1),
          (31, 3, 'THIRD_A', '第三分类 A', 1)
        """);
    CountingJdbcTemplate countingJdbcTemplate = new CountingJdbcTemplate(jdbcTemplate.getDataSource());
    TagRepository countedRepository = new TagRepository(countingJdbcTemplate, new ObjectMapper());

    List<TagCategory> tree = countedRepository.listTree();

    assertThat(tree).extracting(TagCategory::categoryKey).containsExactly("first", "second", "third");
    assertThat(tree.get(0).values()).extracting(TagValue::tagValue).containsExactly("FIRST_A", "FIRST_B");
    assertThat(tree.get(1).values()).extracting(TagValue::tagValue).containsExactly("SECOND_A");
    assertThat(tree.get(2).values()).extracting(TagValue::tagValue).containsExactly("THIRD_A");
    assertThat(countingJdbcTemplate.queryCount()).isEqualTo(2);
  }

  @Test
  void createsAndUpdatesCompleteMetadataWithoutHardcodedLimits() {
    TagCategoryRequest categoryRequest = new TagCategoryRequest(
        "服务偏好",
        "记录客户明确表达的服务偏好",
        "sourceChannel",
        TagSelectionMode.MULTI,
        true,
        true,
        TagAutoUpdateMode.ADD_ONLY,
        new BigDecimal("0.9200"),
        2,
        12,
        TagUncertainPolicy.KEEP_CURRENT,
        true,
        true,
        true,
        false,
        true,
        5);
    repository.createCategory("service_preference_x1", categoryRequest, 5);
    TagCategory createdCategory = repository.listTree().stream()
        .filter(category -> "service_preference_x1".equals(category.categoryKey()))
        .findFirst()
        .orElseThrow();
    long categoryId = createdCategory.id();
    assertThat(createdCategory.selectionMode()).isEqualTo(TagSelectionMode.MULTI);
    assertThat(createdCategory.minConfidence()).isEqualByComparingTo("0.9200");

    TagValueRequest valueRequest = new TagValueRequest(
        categoryId,
        "QUIET_SERVICE",
        "偏好安静服务",
        "客户明确偏好安静、少打扰的服务过程",
        "客户主动要求安静环境",
        "仅暂时不方便说话",
        "客户说希望过程安静一点",
        "客户只说现在在开会",
        List.of("安静服务", "少打扰"),
        true,
        true,
        true,
        1);
    repository.createValue("QUIET_SERVICE", valueRequest, 1);
    long valueId = jdbcTemplate.queryForObject(
        "SELECT id FROM tag_values WHERE category_id = ? AND tag_value = ?",
        Long.class,
        categoryId,
        "QUIET_SERVICE");
    TagValue createdValue = repository.findValue(valueId).orElseThrow();
    assertThat(createdValue.meaning()).contains("安静");
    assertThat(createdValue.synonyms()).containsExactly("安静服务", "少打扰");

    TagValueRequest updateWithoutVersion = new TagValueRequest(
        null, null, "安静沟通", "偏好低打扰沟通", null, null, null, null,
        List.of("安静沟通"), false, true, true, 2);
    assertThat(repository.updateValue(valueId, updateWithoutVersion)).isZero();
    assertThat(repository.updateValue(valueId, new TagValueRequest(
        null, null, "安静沟通", "偏好低打扰沟通", null, null, null, null,
        List.of("安静沟通"), false, true, true, 2, createdValue.version()))).isEqualTo(1);
    TagValue updated = repository.findValue(valueId).orElseThrow();
    assertThat(updated.displayName()).isEqualTo("安静沟通");
    assertThat(updated.systemSelectable()).isFalse();
    assertThat(updated.version()).isEqualTo(1);
  }

  @Test
  void impactsIgnoreMatchingLegacyCustomerColumnsWithoutAssignments() {
    jdbcTemplate.update("""
        INSERT INTO tag_categories (id, category_key, category_name, bound_field, sort_order) VALUES
          (1, 'personality_type', 'Personality', 'personalityType', 1),
          (2, 'body_concerns', 'Body concerns', 'bodyConcerns', 2),
          (3, 'worries', 'Worries', 'worries', 3),
          (4, 'intent_level', 'Intent level', 'intentLevel', 4)
        """);
    jdbcTemplate.update("""
        INSERT INTO tag_values (id, category_id, tag_value, display_name, sort_order) VALUES
          (11, 1, 'LOYALIST', 'Loyalist', 1),
          (21, 2, 'DIASTASIS_RECTI', 'Diastasis recti', 1),
          (31, 3, 'FEAR_EXPENSIVE', 'Price concern', 1),
          (41, 4, 'HIGH', 'High intent', 1)
        """);
    jdbcTemplate.update("""
        INSERT INTO customers (id, personality_type, body_concerns, worries, intent_level)
        VALUES (101, 'LOYALIST', 'DIASTASIS_RECTI', 'FEAR_EXPENSIVE', 'HIGH')
        """);

    for (long categoryId : List.of(1L, 2L, 3L, 4L)) {
      assertThat(repository.categoryImpact(categoryId).customerCount()).isZero();
    }
    for (long valueId : List.of(11L, 21L, 31L, 41L)) {
      TagValue value = repository.findValue(valueId).orElseThrow();
      assertThat(repository.valueImpact(value).customerCount()).isZero();
    }
  }

  @Test
  void impactsCountDistinctAssignmentCustomersAndEveryHistoryRow() {
    TagValue value = insertDirectoryEntry(1L, 11L);
    jdbcTemplate.update("INSERT INTO customers (id) VALUES (101), (202)");
    jdbcTemplate.update("""
        INSERT INTO customer_tag_assignments (customer_id, category_id, tag_value_id, is_active) VALUES
          (101, 1, 11, 0),
          (101, 1, 11, 1),
          (202, 1, 11, 0)
        """);

    TagImpact categoryImpact = repository.categoryImpact(1L);
    TagImpact valueImpact = repository.valueImpact(value);

    assertThat(categoryImpact.customerCount()).isEqualTo(2);
    assertThat(categoryImpact.historyCount()).isEqualTo(3);
    assertThat(categoryImpact.activeAssignmentCount()).isEqualTo(1);
    assertThat(valueImpact.customerCount()).isEqualTo(2);
    assertThat(valueImpact.historyCount()).isEqualTo(3);
    assertThat(valueImpact.activeAssignmentCount()).isEqualTo(1);
  }

  @Test
  void activeAssignmentCountRequiresEnabledUnmergedCategoryAndValue() {
    TagValue value = insertDirectoryEntry(1L, 11L);
    jdbcTemplate.update("INSERT INTO customers (id) VALUES (101)");
    jdbcTemplate.update("""
        INSERT INTO customer_tag_assignments (customer_id, category_id, tag_value_id, is_active)
        VALUES (101, 1, 11, 1)
        """);

    assertThat(repository.categoryImpact(1L).activeAssignmentCount()).isEqualTo(1);
    assertThat(repository.valueImpact(value).activeAssignmentCount()).isEqualTo(1);

    jdbcTemplate.update("UPDATE tag_values SET is_enabled = 0 WHERE id = 11");
    assertInactiveHistoryRemains(value);

    jdbcTemplate.update("UPDATE tag_values SET is_enabled = 1 WHERE id = 11");
    jdbcTemplate.update("UPDATE tag_categories SET is_enabled = 0 WHERE id = 1");
    assertInactiveHistoryRemains(value);

    jdbcTemplate.update("UPDATE tag_categories SET is_enabled = 1 WHERE id = 1");
    jdbcTemplate.update("UPDATE tag_values SET merged_into_id = 12 WHERE id = 11");
    assertInactiveHistoryRemains(value);

    jdbcTemplate.update("UPDATE tag_values SET merged_into_id = NULL WHERE id = 11");
    jdbcTemplate.update("UPDATE tag_categories SET merged_into_id = 2 WHERE id = 1");
    assertInactiveHistoryRemains(value);
  }

  private void createImpactTables() {
    jdbcTemplate.execute("""
        CREATE TABLE customers (
          id BIGINT PRIMARY KEY,
          personality_type VARCHAR(500),
          body_concerns VARCHAR(500),
          worries VARCHAR(500),
          intent_level VARCHAR(500)
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE customer_tag_assignments (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          customer_id BIGINT NOT NULL,
          category_id BIGINT NOT NULL,
          tag_value_id BIGINT NOT NULL,
          is_active TINYINT NOT NULL DEFAULT 1
        )
        """);
    jdbcTemplate.execute("CREATE TABLE tag_analysis_results (category_id BIGINT, tag_value_id BIGINT)");
    jdbcTemplate.execute("CREATE TABLE tag_legacy_value_mappings (category_id BIGINT, tag_value_id BIGINT)");
    jdbcTemplate.execute("CREATE TABLE system_tag_suggestions (tag_value_id BIGINT)");
    jdbcTemplate.execute("CREATE TABLE unmatched_legacy_tag_values (category_id BIGINT, mapped_tag_value_id BIGINT)");
    jdbcTemplate.execute("CREATE TABLE customer_tag_category_locks (category_id BIGINT)");
    jdbcTemplate.execute("CREATE TABLE personality_tags (canonical_tag_value_id BIGINT)");
  }

  private TagValue insertDirectoryEntry(long categoryId, long valueId) {
    jdbcTemplate.update("""
        INSERT INTO tag_categories (id, category_key, category_name, sort_order)
        VALUES (?, ?, ?, 1)
        """, categoryId, "category_" + categoryId, "Category " + categoryId);
    jdbcTemplate.update("""
        INSERT INTO tag_values (id, category_id, tag_value, display_name, sort_order)
        VALUES (?, ?, ?, ?, 1)
        """, valueId, categoryId, "VALUE_" + valueId, "Value " + valueId);
    return repository.findValue(valueId).orElseThrow();
  }

  private void assertInactiveHistoryRemains(TagValue value) {
    TagImpact categoryImpact = repository.categoryImpact(value.categoryId());
    TagImpact valueImpact = repository.valueImpact(value);
    assertThat(categoryImpact.activeAssignmentCount()).isZero();
    assertThat(categoryImpact.customerCount()).isEqualTo(1);
    assertThat(categoryImpact.historyCount()).isEqualTo(1);
    assertThat(valueImpact.activeAssignmentCount()).isZero();
    assertThat(valueImpact.customerCount()).isEqualTo(1);
    assertThat(valueImpact.historyCount()).isEqualTo(1);
  }

  private static final class CountingJdbcTemplate extends JdbcTemplate {

    private final AtomicInteger queryCount = new AtomicInteger();

    private CountingJdbcTemplate(DataSource dataSource) {
      super(dataSource);
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
      queryCount.incrementAndGet();
      return super.query(sql, rowMapper);
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      queryCount.incrementAndGet();
      return super.query(sql, rowMapper, args);
    }

    private int queryCount() {
      return queryCount.get();
    }
  }
}
