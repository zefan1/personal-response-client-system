package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import com.privateflow.modules.skill.TagAnalysisAction;
import com.privateflow.modules.skill.TagAnalysisDecision;
import com.privateflow.modules.skill.TagAnalysisResultType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CustomerTagUpdateRepositoryTest {

  private JdbcTemplate jdbcTemplate;
  private CustomerTagUpdateRepository repository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:customer_tag_update;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    dropTables();
    createTables();
    repository = new CustomerTagUpdateRepository(
        jdbcTemplate,
        mock(CustomerTagFoundationRepository.class));
  }

  @Test
  void singleReplaceUpdatesLegacyFieldAssignmentsAnalysisAndAuditTogether() {
    jdbcTemplate.update("INSERT INTO customers (id, phone, intent_level, version) VALUES (7, '18800001111', 'LOW', 3)");
    jdbcTemplate.update("""
        INSERT INTO customer_tag_assignments (
          id, customer_id, category_id, tag_value_id, selection_mode, is_active,
          source_type, evidence_message_count, is_manual_locked, customer_version,
          created_at, updated_at
        ) VALUES (21, 7, 1, 11, 'SINGLE', 1, 'SYSTEM_INFERENCE', 4, 0, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """);
    AutomaticCustomerTagUpdatePlan plan = plan(3);
    AtomicReference<CustomerTagUpdateResult> result = new AtomicReference<>();

    assertThatCode(() -> result.set(repository.applyAutomatic(plan))).doesNotThrowAnyException();

    assertThat(result.get().updated()).isTrue();
    assertThat(result.get().customerVersion()).isEqualTo(4);
    assertThat(jdbcTemplate.queryForMap("SELECT intent_level, version FROM customers WHERE id = 7"))
        .containsEntry("intent_level", "HIGH")
        .containsEntry("version", 4);
    assertThat(jdbcTemplate.queryForMap("SELECT is_active, invalidated_reason FROM customer_tag_assignments WHERE id = 21"))
        .containsEntry("is_active", 0)
        .containsEntry("invalidated_reason", "AUTO_REPLACED");
    assertThat(jdbcTemplate.queryForMap("""
        SELECT tag_value_id, source_type, confidence, evidence_text, evidence_message_count,
               supersedes_assignment_id, customer_version
        FROM customer_tag_assignments WHERE id <> 21
        """))
        .containsEntry("tag_value_id", 12L)
        .containsEntry("source_type", "SYSTEM_INFERENCE")
        .containsEntry("evidence_message_count", 5)
        .containsEntry("supersedes_assignment_id", 21L)
        .containsEntry("customer_version", 4);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tag_analysis_runs", Integer.class)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForMap("SELECT validation_status, validation_reason FROM tag_analysis_results"))
        .containsEntry("validation_status", "ACCEPTED")
        .containsEntry("validation_reason", "自动标签更新校验通过");
    assertThat(jdbcTemplate.queryForMap("SELECT action, operator, target_type, target_id FROM audit_logs"))
        .containsEntry("action", "AUTO_UPDATE_CUSTOMER_TAGS")
        .containsEntry("operator", "keeper-1")
        .containsEntry("target_type", "CUSTOMER")
        .containsEntry("target_id", "7");
  }

  @Test
  void manualReplaceWritesAssignmentLegacyFieldDefaultLockAndAuditTogether() {
    jdbcTemplate.update("INSERT INTO customers (id, phone, intent_level, version) VALUES (7, '18800001111', 'LOW', 3)");
    jdbcTemplate.update("""
        INSERT INTO customer_tag_assignments (
          id, customer_id, category_id, tag_value_id, selection_mode, is_active,
          source_type, evidence_message_count, is_manual_locked, customer_version,
          created_at, updated_at
        ) VALUES (21, 7, 1, 11, 'SINGLE', 1, 'SYSTEM_INFERENCE', 4, 0, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """);
    AutomaticCustomerTagUpdatePlan automaticPlan = plan(3);
    AutomaticCustomerTagDecisionPlan automaticDecision = automaticPlan.decisions().get(0);
    ManualCustomerTagUpdatePlan manualPlan = new ManualCustomerTagUpdatePlan(
        7L,
        "18800001111",
        3,
        automaticDecision.category(),
        automaticDecision.values(),
        automaticDecision.previousAssignments(),
        "keeper-auth",
        "客户明确确认购买",
        true,
        LocalDateTime.of(2026, 7, 15, 10, 0));
    AtomicReference<CustomerTagUpdateResult> result = new AtomicReference<>();

    assertThatCode(() -> result.set(repository.applyManual(manualPlan))).doesNotThrowAnyException();

    assertThat(result.get().updated()).isTrue();
    assertThat(result.get().customerVersion()).isEqualTo(4);
    assertThat(jdbcTemplate.queryForMap("SELECT intent_level, version FROM customers WHERE id = 7"))
        .containsEntry("intent_level", "HIGH")
        .containsEntry("version", 4);
    assertThat(jdbcTemplate.queryForMap("SELECT is_active, invalidated_reason FROM customer_tag_assignments WHERE id = 21"))
        .containsEntry("is_active", 0)
        .containsEntry("invalidated_reason", "MANUAL_REPLACED");
    assertThat(jdbcTemplate.queryForMap("""
        SELECT tag_value_id, source_type, operator_account, is_manual_locked,
               locked_by, supersedes_assignment_id, customer_version
        FROM customer_tag_assignments WHERE id <> 21
        """))
        .containsEntry("tag_value_id", 12L)
        .containsEntry("source_type", "MANUAL")
        .containsEntry("operator_account", "keeper-auth")
        .containsEntry("is_manual_locked", 1)
        .containsEntry("locked_by", "keeper-auth")
        .containsEntry("supersedes_assignment_id", 21L)
        .containsEntry("customer_version", 4);
    assertThat(jdbcTemplate.queryForMap("""
        SELECT is_locked, locked_by, lock_reason, version
        FROM customer_tag_category_locks WHERE customer_id = 7 AND category_id = 1
        """))
        .containsEntry("is_locked", 1)
        .containsEntry("locked_by", "keeper-auth")
        .containsEntry("lock_reason", "客户明确确认购买")
        .containsEntry("version", 0);
    assertThat(jdbcTemplate.queryForMap("SELECT action, operator FROM audit_logs"))
        .containsEntry("action", "MANUAL_UPDATE_CUSTOMER_TAGS")
        .containsEntry("operator", "keeper-auth");
  }

  @Test
  void unlockUpdatesCustomerVersionAssignmentLockAndAuditTogether() {
    jdbcTemplate.update("INSERT INTO customers (id, phone, intent_level, version) VALUES (7, '18800001111', 'HIGH', 3)");
    jdbcTemplate.update("""
        INSERT INTO customer_tag_assignments (
          id, customer_id, category_id, tag_value_id, selection_mode, is_active,
          source_type, evidence_message_count, operator_account, is_manual_locked,
          locked_by, locked_at, customer_version, created_at, updated_at
        ) VALUES (21, 7, 1, 12, 'SINGLE', 1, 'MANUAL', 0, 'keeper-auth', 1,
          'keeper-auth', CURRENT_TIMESTAMP, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """);
    jdbcTemplate.update("""
        INSERT INTO customer_tag_category_locks (
          id, customer_id, category_id, is_locked, locked_by, lock_reason,
          locked_at, version, created_at, updated_at
        ) VALUES (31, 7, 1, 1, 'keeper-auth', '人工修改', CURRENT_TIMESTAMP,
          0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """);
    AutomaticCustomerTagUpdatePlan automaticPlan = plan(3);
    TagCategory category = automaticPlan.decisions().get(0).category();
    CustomerTagLockUpdatePlan lockPlan = new CustomerTagLockUpdatePlan(
        7L,
        "18800001111",
        3,
        category,
        false,
        "leader-auth",
        "客户主动要求重新判断",
        LocalDateTime.of(2026, 7, 15, 10, 0));
    AtomicReference<CustomerTagUpdateResult> result = new AtomicReference<>();

    assertThatCode(() -> result.set(repository.applyLock(lockPlan))).doesNotThrowAnyException();

    assertThat(result.get().updated()).isTrue();
    assertThat(result.get().customerVersion()).isEqualTo(4);
    assertThat(jdbcTemplate.queryForMap("SELECT version FROM customers WHERE id = 7"))
        .containsEntry("version", 4);
    assertThat(jdbcTemplate.queryForMap("""
        SELECT is_locked, unlocked_by, lock_reason, version
        FROM customer_tag_category_locks WHERE customer_id = 7 AND category_id = 1
        """))
        .containsEntry("is_locked", 0)
        .containsEntry("unlocked_by", "leader-auth")
        .containsEntry("lock_reason", "客户主动要求重新判断")
        .containsEntry("version", 1);
    assertThat(jdbcTemplate.queryForMap("""
        SELECT is_manual_locked, locked_by FROM customer_tag_assignments WHERE id = 21
        """))
        .containsEntry("is_manual_locked", 0)
        .containsEntry("locked_by", null);
    assertThat(jdbcTemplate.queryForMap("SELECT action, operator FROM audit_logs"))
        .containsEntry("action", "MANUAL_UNLOCK_CUSTOMER_TAGS")
        .containsEntry("operator", "leader-auth");
  }

  private AutomaticCustomerTagUpdatePlan plan(int expectedVersion) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
    TagValue current = value(11L, "LOW", now);
    TagValue requested = value(12L, "HIGH", now);
    TagCategory category = new TagCategory(
        1L, "intent_level", "意向度", "识别客户购买意向", "intentLevel",
        TagSelectionMode.SINGLE, true, true, TagAutoUpdateMode.REPLACE,
        new BigDecimal("0.8500"), 3, 24, TagUncertainPolicy.KEEP_CURRENT,
        true, true, true, true, true, true, 1, null, 0,
        List.of(current, requested), TagImpact.empty(), now, now);
    TagAnalysisDecision analysis = new TagAnalysisDecision(
        "intent_level",
        List.of("HIGH"),
        new BigDecimal("0.9200"),
        "客户明确表示本周到店并询问付款方式",
        TagAnalysisResultType.UPDATE,
        TagAnalysisAction.REPLACE);
    AutomaticCustomerTagDecisionPlan decision = new AutomaticCustomerTagDecisionPlan(
        category,
        List.of(requested),
        List.of(assignment(21L, current, now)),
        TagAnalysisAction.REPLACE,
        true,
        "自动标签更新校验通过",
        analysis);
    return new AutomaticCustomerTagUpdatePlan(
        "analysis-key-1", 7L, "18800001111", expectedVersion, 5, "keeper-1", now, List.of(decision));
  }

  private TagValue value(long id, String code, LocalDateTime now) {
    return new TagValue(
        id, 1L, "intent_level", code, code, "", "", "", "", "", List.of(),
        true, true, true, (int) id, null, 0, TagImpact.empty(), now, now);
  }

  private CustomerTagAssignment assignment(long id, TagValue value, LocalDateTime now) {
    return new CustomerTagAssignment(
        id, 7L, 1L, value.id(), TagSelectionMode.SINGLE, true,
        "SYSTEM_INFERENCE", new BigDecimal("0.9000"), "旧证据", 4,
        null, null, null, null, null, "SYSTEM", false, null, null,
        null, 2, null, null, now, now, value.id(), 1L);
  }

  private void dropTables() {
    jdbcTemplate.execute("DROP TABLE IF EXISTS audit_logs");
    jdbcTemplate.execute("DROP TABLE IF EXISTS customer_tag_category_locks");
    jdbcTemplate.execute("DROP TABLE IF EXISTS customer_tag_assignments");
    jdbcTemplate.execute("DROP TABLE IF EXISTS tag_analysis_results");
    jdbcTemplate.execute("DROP TABLE IF EXISTS tag_analysis_runs");
    jdbcTemplate.execute("DROP TABLE IF EXISTS customers");
  }

  private void createTables() {
    jdbcTemplate.execute("""
        CREATE TABLE customers (
          id BIGINT PRIMARY KEY,
          phone VARCHAR(30) NOT NULL,
          intent_level VARCHAR(100),
          version INT NOT NULL,
          updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE tag_analysis_runs (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          analysis_key VARCHAR(64) NOT NULL,
          customer_id BIGINT NOT NULL,
          source_type VARCHAR(32) NOT NULL,
          status VARCHAR(20) NOT NULL,
          effective_message_count INT NOT NULL,
          customer_version INT NOT NULL,
          caller VARCHAR(100),
          skill_id VARCHAR(100),
          llm_environment VARCHAR(100),
          llm_model VARCHAR(100),
          prompt_version VARCHAR(100),
          error_message VARCHAR(1000),
          started_at DATETIME NOT NULL,
          finished_at DATETIME,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE tag_analysis_results (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          analysis_run_id BIGINT NOT NULL,
          category_id BIGINT NOT NULL,
          tag_value_id BIGINT,
          result_type VARCHAR(24) NOT NULL,
          requested_action VARCHAR(20) NOT NULL,
          confidence DECIMAL(5,4),
          evidence_text VARCHAR(2000),
          validation_status VARCHAR(20) NOT NULL,
          validation_reason VARCHAR(1000),
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE customer_tag_assignments (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          customer_id BIGINT NOT NULL,
          category_id BIGINT NOT NULL,
          tag_value_id BIGINT NOT NULL,
          selection_mode VARCHAR(16) NOT NULL,
          is_active TINYINT NOT NULL,
          source_type VARCHAR(32) NOT NULL,
          confidence DECIMAL(5,4),
          evidence_text VARCHAR(2000),
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
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE customer_tag_category_locks (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          customer_id BIGINT NOT NULL,
          category_id BIGINT NOT NULL,
          is_locked TINYINT NOT NULL,
          locked_by VARCHAR(100) NOT NULL,
          lock_reason VARCHAR(500),
          locked_at DATETIME NOT NULL,
          unlocked_by VARCHAR(100),
          unlocked_at DATETIME,
          version INT NOT NULL,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          UNIQUE (customer_id, category_id)
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE audit_logs (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          action VARCHAR(100) NOT NULL,
          operator VARCHAR(100) NOT NULL,
          target_type VARCHAR(100) NOT NULL,
          target_id VARCHAR(100) NOT NULL,
          detail VARCHAR(1000),
          created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
        """);
  }
}
