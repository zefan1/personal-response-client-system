package com.privateflow.modules.supervision;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.chat.ChatTaskConfig;
import com.privateflow.modules.api.chat.PendingReplyTaskRepository;
import com.privateflow.modules.api.chat.PendingReplyTaskStatus;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.llm.LlmCallAnalyticsRepository;
import com.privateflow.modules.skill.admin.SkillCallAnalyticsRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SupervisionCleanupSchedulerTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 4, 10);

  private JdbcTemplate jdbcTemplate;
  private SystemConfigRepository configRepository;
  private SupervisionConfig supervisionConfig;
  private ChatTaskConfig chatTaskConfig;
  private SupervisionCleanupScheduler scheduler;

  @BeforeEach
  void setUp() {
    jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:supervision_cleanup_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        ""));
    jdbcTemplate.execute("""
        CREATE TABLE system_configs (
          config_key VARCHAR(100) PRIMARY KEY,
          config_value VARCHAR(255) NOT NULL,
          description VARCHAR(255)
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE supervision_events (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          occurred_at TIMESTAMP NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE llm_call_logs (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          created_at TIMESTAMP NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE skill_call_logs (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          created_at TIMESTAMP NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE pending_reply_tasks (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          task_id VARCHAR(36) NOT NULL UNIQUE,
          reply_session_id VARCHAR(80) NOT NULL,
          username VARCHAR(64) NOT NULL,
          status VARCHAR(32) NOT NULL,
          client_message VARCHAR(255) NOT NULL,
          chat_context_json VARCHAR(255) NOT NULL,
          selected_phone VARCHAR(32),
          result_json VARCHAR(255),
          error_code VARCHAR(32),
          generation_started_at TIMESTAMP,
          finished_at TIMESTAMP,
          expires_at TIMESTAMP NOT NULL,
          created_at TIMESTAMP NOT NULL,
          updated_at TIMESTAMP NOT NULL,
          version INT NOT NULL DEFAULT 0
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE pending_reply_task_candidates (
          task_id BIGINT NOT NULL,
          phone VARCHAR(32) NOT NULL,
          rank_no SMALLINT NOT NULL,
          PRIMARY KEY (task_id, phone)
        )
        """);
    configRepository = new SystemConfigRepository(jdbcTemplate);
    supervisionConfig = new SupervisionConfig(configRepository);
    chatTaskConfig = new ChatTaskConfig(configRepository);
    scheduler = new SupervisionCleanupScheduler(
        supervisionConfig,
        chatTaskConfig,
        new SupervisionEventRepository(jdbcTemplate, new ObjectMapper()),
        new LlmCallAnalyticsRepository(jdbcTemplate),
        new SkillCallAnalyticsRepository(jdbcTemplate),
        new PendingReplyTaskRepository(jdbcTemplate, new ObjectMapper()));
  }

  @Test
  void governanceConfigUsesDefaultsRefreshesAndKeepsLastValidSnapshot() {
    assertThat(supervisionConfig.recordRetentionDays()).isEqualTo(180);
    assertThat(supervisionConfig.technicalLogRetentionDays()).isEqualTo(30);
    assertThat(supervisionConfig.expiredReplyTaskRetentionDays()).isEqualTo(3);
    assertThat(supervisionConfig.unfinishedTaskCap()).isEqualTo(20);
    assertThat(supervisionConfig.recentTaskDisplayCap()).isEqualTo(30);
    assertThat(supervisionConfig.recognitionConcurrency()).isEqualTo(4);
    assertThat(supervisionConfig.processingSlaMinutes()).isEqualTo(1440);

    putConfig("supervision.record_retention_days", "200");
    putConfig("chat.recognition_concurrency", "8");
    supervisionConfig.onConfigChanged(new ConfigChangedEvent("chat.recognition_concurrency"));

    assertThat(supervisionConfig.recordRetentionDays()).isEqualTo(200);
    assertThat(supervisionConfig.recognitionConcurrency()).isEqualTo(8);

    putConfig("supervision.technical_log_retention_days", "181");
    supervisionConfig.onConfigChanged(new ConfigChangedEvent("supervision.technical_log_retention_days"));

    assertThat(supervisionConfig.recordRetentionDays()).isEqualTo(200);
    assertThat(supervisionConfig.technicalLogRetentionDays()).isEqualTo(30);
    assertThat(supervisionConfig.recognitionConcurrency()).isEqualTo(8);

    putConfig("supervision.technical_log_retention_days", "30");
    putConfig("supervision.record_retention_days", "");
    supervisionConfig.onConfigChanged(new ConfigChangedEvent("supervision.record_retention_days"));

    assertThat(supervisionConfig.recordRetentionDays()).isEqualTo(200);
    assertThat(supervisionConfig.technicalLogRetentionDays()).isEqualTo(30);
    assertThat(supervisionConfig.recognitionConcurrency()).isEqualTo(8);

    putConfig("supervision.record_retention_days", "not-a-number");
    supervisionConfig.onConfigChanged(new ConfigChangedEvent("supervision.record_retention_days"));

    assertThat(supervisionConfig.recordRetentionDays()).isEqualTo(200);
    assertThat(supervisionConfig.technicalLogRetentionDays()).isEqualTo(30);
    assertThat(supervisionConfig.recognitionConcurrency()).isEqualTo(8);
  }

  @Test
  void governanceConfigKeepsItsLastSnapshotWhenConfigurationReadFails() {
    FailingSystemConfigRepository failingRepository = new FailingSystemConfigRepository(jdbcTemplate);
    SupervisionConfig config = new SupervisionConfig(failingRepository);
    putConfig("supervision.record_retention_days", "200");
    putConfig("chat.recognition_concurrency", "8");
    config.onConfigChanged(new ConfigChangedEvent("supervision.record_retention_days"));

    failingRepository.failReads();
    config.onConfigChanged(new ConfigChangedEvent("supervision.record_retention_days"));

    assertThat(config.recordRetentionDays()).isEqualTo(200);
    assertThat(config.technicalLogRetentionDays()).isEqualTo(30);
    assertThat(config.recognitionConcurrency()).isEqualTo(8);
  }

  @Test
  void cleanupUsesIndependentRetentionWindowsAndChatTimeoutSnapshot() {
    putConfig("supervision.technical_log_retention_days", "7");
    putConfig("chat.expired_reply_task_retention_days", "3");
    putConfig("chat.pending_reply_generating_timeout_s", "300");
    supervisionConfig.onConfigChanged(new ConfigChangedEvent("supervision.technical_log_retention_days"));
    chatTaskConfig.onConfigChanged(new ConfigChangedEvent("chat.pending_reply_generating_timeout_s"));

    insertSupervisionEvent(NOW.minusDays(180).minusSeconds(1));
    insertSupervisionEvent(NOW.minusDays(180));
    insertSupervisionEvent(NOW.minusDays(8));
    insertLlmLog(NOW.minusDays(7).minusSeconds(1));
    insertLlmLog(NOW.minusDays(7));
    insertSkillLog(NOW.minusDays(7).minusSeconds(1));
    insertSkillLog(NOW.minusDays(7));
    insertTask("ready-old", PendingReplyTaskStatus.READY, NOW.minusDays(3).minusSeconds(1), null);
    insertTask("ready-boundary", PendingReplyTaskStatus.READY, NOW.minusDays(3), null);
    insertTask("generating-within-chat-timeout", PendingReplyTaskStatus.GENERATING,
        NOW.plusHours(1), NOW.minusSeconds(121));

    scheduler.cleanupAt(NOW);

    assertThat(countRows("supervision_events")).isEqualTo(2);
    assertThat(countRows("llm_call_logs")).isEqualTo(1);
    assertThat(countRows("skill_call_logs")).isEqualTo(1);
    assertThat(taskExists("ready-old")).isFalse();
    assertThat(taskExists("ready-boundary")).isTrue();
    assertThat(taskStatus("generating-within-chat-timeout"))
        .isEqualTo(PendingReplyTaskStatus.GENERATING.name());
  }

  private void putConfig(String key, String value) {
    jdbcTemplate.update("""
        MERGE INTO system_configs (config_key, config_value, description)
        KEY (config_key) VALUES (?, ?, ?)
        """, key, value, key);
  }

  private void insertSupervisionEvent(LocalDateTime occurredAt) {
    jdbcTemplate.update(
        "INSERT INTO supervision_events (occurred_at) VALUES (?)",
        Timestamp.valueOf(occurredAt));
  }

  private void insertLlmLog(LocalDateTime createdAt) {
    jdbcTemplate.update(
        "INSERT INTO llm_call_logs (created_at) VALUES (?)",
        Timestamp.valueOf(createdAt));
  }

  private void insertSkillLog(LocalDateTime createdAt) {
    jdbcTemplate.update(
        "INSERT INTO skill_call_logs (created_at) VALUES (?)",
        Timestamp.valueOf(createdAt));
  }

  private void insertTask(
      String taskId,
      PendingReplyTaskStatus status,
      LocalDateTime expiresAt,
      LocalDateTime generationStartedAt) {
    jdbcTemplate.update("""
        INSERT INTO pending_reply_tasks (
          task_id, reply_session_id, username, status, client_message, chat_context_json,
          generation_started_at, expires_at, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        taskId,
        "session-" + taskId,
        "keeper-1",
        status.name(),
        "message",
        "[]",
        generationStartedAt == null ? null : Timestamp.valueOf(generationStartedAt),
        Timestamp.valueOf(expiresAt),
        Timestamp.valueOf(NOW.minusDays(10)),
        Timestamp.valueOf(NOW.minusDays(10)));
  }

  private int countRows(String table) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    return count == null ? 0 : count;
  }

  private boolean taskExists(String taskId) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM pending_reply_tasks WHERE task_id = ?", Integer.class, taskId);
    return count != null && count == 1;
  }

  private String taskStatus(String taskId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM pending_reply_tasks WHERE task_id = ?", String.class, taskId);
  }

  private static final class FailingSystemConfigRepository extends SystemConfigRepository {

    private boolean failReads;

    private FailingSystemConfigRepository(JdbcTemplate jdbcTemplate) {
      super(jdbcTemplate);
    }

    @Override
    public Map<String, String> findByPrefix(String prefix) {
      if (failReads) {
        throw new IllegalStateException("configuration store unavailable");
      }
      return super.findByPrefix(prefix);
    }

    private void failReads() {
      failReads = true;
    }
  }
}
