package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CustomerTagFoundationRepositoryTest {

  private JdbcTemplate jdbcTemplate;
  private CountingJdbcTemplate countingJdbcTemplate;
  private CustomerTagFoundationRepository repository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:customer_tag_foundation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS customer_tag_assignments");
    jdbcTemplate.execute("DROP TABLE IF EXISTS tag_values");
    jdbcTemplate.execute("DROP TABLE IF EXISTS tag_categories");
    jdbcTemplate.execute("""
        CREATE TABLE tag_categories (
          id BIGINT PRIMARY KEY,
          category_key VARCHAR(50) NOT NULL,
          category_name VARCHAR(100) NOT NULL,
          selection_mode VARCHAR(16) NOT NULL,
          is_enabled TINYINT NOT NULL,
          sort_order INT NOT NULL,
          merged_into_id BIGINT,
          version INT NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE tag_values (
          id BIGINT PRIMARY KEY,
          category_id BIGINT NOT NULL,
          tag_value VARCHAR(50) NOT NULL,
          display_name VARCHAR(100) NOT NULL,
          is_enabled TINYINT NOT NULL,
          sort_order INT NOT NULL,
          merged_into_id BIGINT,
          version INT NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE customer_tag_assignments (
          id BIGINT PRIMARY KEY,
          customer_id BIGINT NOT NULL,
          category_id BIGINT NOT NULL,
          tag_value_id BIGINT NOT NULL,
          selection_mode VARCHAR(16) NOT NULL,
          is_active TINYINT NOT NULL,
          source_type VARCHAR(50),
          confidence DECIMAL(5,4),
          evidence_text VARCHAR(1000),
          evidence_message_count INT NOT NULL,
          analysis_result_id BIGINT,
          skill_id VARCHAR(100),
          llm_environment VARCHAR(100),
          llm_model VARCHAR(100),
          prompt_version VARCHAR(100),
          operator_account VARCHAR(100),
          is_manual_locked TINYINT NOT NULL,
          locked_by VARCHAR(100),
          locked_at DATETIME,
          supersedes_assignment_id BIGINT,
          customer_version INT NOT NULL,
          invalidated_reason VARCHAR(500),
          invalidated_at DATETIME,
          created_at DATETIME NOT NULL,
          updated_at DATETIME NOT NULL,
          active_tag_key BIGINT,
          active_single_category_key BIGINT
        )
        """);
    countingJdbcTemplate = new CountingJdbcTemplate(dataSource);
    repository = new CustomerTagFoundationRepository(countingJdbcTemplate);
  }

  @Test
  void currentUsesOneJoinedQueryMapsCompleteDtoAndFiltersNonCurrentDirectoryState() {
    insertCategory(10L, true, null, 4);
    insertValue(20L, 10L, true, null, 5);
    insertAssignment(101L, 10L, 20L, true, null, null);
    insertAssignment(102L, 10L, 20L, false, "已替代", "2026-07-15 08:30:00");

    insertCategory(30L, false, null, 6);
    insertValue(31L, 30L, true, null, 7);
    insertAssignment(103L, 30L, 31L, true, null, null);

    insertCategory(40L, true, 10L, 8);
    insertValue(41L, 40L, true, null, 9);
    insertAssignment(104L, 40L, 41L, true, null, null);

    insertCategory(50L, true, null, 10);
    insertValue(51L, 50L, false, null, 11);
    insertAssignment(105L, 50L, 51L, true, null, null);

    insertCategory(60L, true, null, 12);
    insertValue(61L, 60L, true, 20L, 13);
    insertAssignment(106L, 60L, 61L, true, null, null);

    List<CustomerTagQueryDto> result = repository.findCurrentTagDetails(7L);

    assertThat(result).singleElement().satisfies(item -> {
      assertThat(item.assignmentId()).isEqualTo(101L);
      assertThat(item.customerId()).isEqualTo(7L);
      assertThat(item.customerVersion()).isEqualTo(3);
      assertThat(item.categoryId()).isEqualTo(10L);
      assertThat(item.categoryKey()).isEqualTo("category_10");
      assertThat(item.categoryName()).isEqualTo("分类 10");
      assertThat(item.categorySelectionMode()).isEqualTo(TagSelectionMode.SINGLE);
      assertThat(item.categoryEnabled()).isTrue();
      assertThat(item.categoryVersion()).isEqualTo(4);
      assertThat(item.tagValueId()).isEqualTo(20L);
      assertThat(item.tagValue()).isEqualTo("VALUE_20");
      assertThat(item.tagDisplayName()).isEqualTo("标签 20");
      assertThat(item.tagValueEnabled()).isTrue();
      assertThat(item.tagValueVersion()).isEqualTo(5);
      assertThat(item.sourceType()).isEqualTo("SKILL");
      assertThat(item.confidence()).isEqualByComparingTo("0.9300");
      assertThat(item.evidenceText()).isEqualTo("客户连续询问价格和到店时间");
      assertThat(item.evidenceMessageCount()).isEqualTo(6);
      assertThat(item.analysisResultId()).isEqualTo(301L);
      assertThat(item.skillId()).isEqualTo("profile-analysis");
      assertThat(item.llmEnvironment()).isEqualTo("prod");
      assertThat(item.llmModel()).isEqualTo("gpt-5.1");
      assertThat(item.promptVersion()).isEqualTo("prompt-v3");
      assertThat(item.operatorAccount()).isEqualTo("keeper-13800000000");
      assertThat(item.manualLocked()).isTrue();
      assertThat(item.lockedBy()).isEqualTo("leader-13900000000");
      assertThat(item.lockedAt()).isEqualTo(LocalDateTime.of(2026, 7, 14, 9, 0));
      assertThat(item.supersedesAssignmentId()).isEqualTo(99L);
      assertThat(item.createdAt()).isEqualTo(LocalDateTime.of(2026, 7, 14, 10, 0));
      assertThat(item.updatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 14, 10, 5));
    });
    assertThat(countingJdbcTemplate.queryCount()).isEqualTo(1);
  }

  @Test
  void historyReturnsInactiveDisabledMergedStateAndCurrentDirectoryVersions() {
    insertCategory(10L, false, 110L, 14);
    insertValue(20L, 10L, false, 120L, 15);
    insertAssignment(201L, 10L, 20L, false, "标签被新判断替代", "2026-07-15 08:30:00");

    CustomerTagQueryDto item = repository.findTagHistoryDetails(7L, 25).get(0);

    assertThat(item.active()).isFalse();
    assertThat(item.invalidatedReason()).isEqualTo("标签被新判断替代");
    assertThat(item.invalidatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 15, 8, 30));
    assertThat(item.categoryEnabled()).isFalse();
    assertThat(item.categoryMergedIntoId()).isEqualTo(110L);
    assertThat(item.categoryVersion()).isEqualTo(14);
    assertThat(item.tagValueEnabled()).isFalse();
    assertThat(item.tagValueMergedIntoId()).isEqualTo(120L);
    assertThat(item.tagValueVersion()).isEqualTo(15);
  }

  @Test
  void currentFailsFastWithDiagnosticIdsWhenCategoryIsMissing() {
    insertValue(20L, 99L, true, null, 5);
    insertAssignment(301L, 99L, 20L, true, null, null);

    assertThatThrownBy(() -> repository.findCurrentTagDetails(7L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("assignmentId=301")
        .hasMessageContaining("期望categoryId=99")
        .hasMessageContaining("目录categoryId=null")
        .hasMessageContaining("期望valueId=20")
        .hasMessageContaining("实际valueId=20")
        .hasMessageContaining("value实际categoryId=99");
  }

  @Test
  void currentFailsFastWithDiagnosticIdsWhenValueIsMissing() {
    insertCategory(10L, true, null, 4);
    insertAssignment(302L, 10L, 999L, true, null, null);

    assertThatThrownBy(() -> repository.findCurrentTagDetails(7L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("assignmentId=302")
        .hasMessageContaining("期望categoryId=10")
        .hasMessageContaining("目录categoryId=10")
        .hasMessageContaining("期望valueId=999")
        .hasMessageContaining("实际valueId=null")
        .hasMessageContaining("value实际categoryId=null");
  }

  @Test
  void currentFailsFastWithDiagnosticIdsWhenValueBelongsToAnotherCategory() {
    insertCategory(10L, true, null, 4);
    insertValue(20L, 11L, true, null, 5);
    insertAssignment(303L, 10L, 20L, true, null, null);

    assertThatThrownBy(() -> repository.findCurrentTagDetails(7L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("assignmentId=303")
        .hasMessageContaining("期望categoryId=10")
        .hasMessageContaining("目录categoryId=10")
        .hasMessageContaining("期望valueId=20")
        .hasMessageContaining("实际valueId=20")
        .hasMessageContaining("value实际categoryId=11");
  }

  private void insertCategory(long id, boolean enabled, Long mergedIntoId, int version) {
    jdbcTemplate.update("""
        INSERT INTO tag_categories (
          id, category_key, category_name, selection_mode, is_enabled,
          sort_order, merged_into_id, version
        ) VALUES (?, ?, ?, 'SINGLE', ?, ?, ?, ?)
        """,
        id,
        "category_" + id,
        "分类 " + id,
        enabled ? 1 : 0,
        id,
        mergedIntoId,
        version);
  }

  private void insertValue(
      long id,
      long categoryId,
      boolean enabled,
      Long mergedIntoId,
      int version) {
    jdbcTemplate.update("""
        INSERT INTO tag_values (
          id, category_id, tag_value, display_name, is_enabled,
          sort_order, merged_into_id, version
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        categoryId,
        "VALUE_" + id,
        "标签 " + id,
        enabled ? 1 : 0,
        id,
        mergedIntoId,
        version);
  }

  private void insertAssignment(
      long id,
      long categoryId,
      long tagValueId,
      boolean active,
      String invalidatedReason,
      String invalidatedAt) {
    jdbcTemplate.update("""
        INSERT INTO customer_tag_assignments (
          id, customer_id, category_id, tag_value_id, selection_mode, is_active,
          source_type, confidence, evidence_text, evidence_message_count,
          analysis_result_id, skill_id, llm_environment, llm_model, prompt_version,
          operator_account, is_manual_locked, locked_by, locked_at,
          supersedes_assignment_id, customer_version, invalidated_reason, invalidated_at,
          created_at, updated_at, active_tag_key, active_single_category_key
        ) VALUES (
          ?, 7, ?, ?, 'SINGLE', ?, 'SKILL', 0.9300,
          '客户连续询问价格和到店时间', 6, 301, 'profile-analysis', 'prod',
          'gpt-5.1', 'prompt-v3', 'keeper-13800000000', 1,
          'leader-13900000000', '2026-07-14 09:00:00', 99, 3, ?, ?,
          '2026-07-14 10:00:00', '2026-07-14 10:05:00', ?, ?
        )
        """,
        id,
        categoryId,
        tagValueId,
        active ? 1 : 0,
        invalidatedReason,
        invalidatedAt,
        active ? tagValueId : null,
        active ? categoryId : null);
  }

  private static final class CountingJdbcTemplate extends JdbcTemplate {

    private final AtomicInteger queryCount = new AtomicInteger();

    private CountingJdbcTemplate(DataSource dataSource) {
      super(dataSource);
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
