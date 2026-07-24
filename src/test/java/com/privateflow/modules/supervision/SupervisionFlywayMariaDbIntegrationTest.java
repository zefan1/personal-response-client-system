package com.privateflow.modules.supervision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class SupervisionFlywayMariaDbIntegrationTest {

  @Test
  void rejectsMigrationUrlsOutsideTheDedicatedTemporaryDatabasePrefix() {
    assertThatThrownBy(() -> SupervisionFlywayMariaDbIntegrationTest.requireTemporaryMigrationUrl(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pda_v79_it_");
    assertThatThrownBy(() -> SupervisionFlywayMariaDbIntegrationTest.requireTemporaryMigrationUrl(
        "jdbc:mariadb://127.0.0.1:3306/private_domain_assistant_dev"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pda_v79_it_");
    assertThatThrownBy(() -> SupervisionFlywayMariaDbIntegrationTest.requireTemporaryMigrationUrl(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pda_v79_it_");
    assertThatThrownBy(() -> SupervisionFlywayMariaDbIntegrationTest.requireTemporaryMigrationUrl(
        "jdbc:mariadb://127.0.0.1:3306/"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pda_v79_it_");
    assertThatThrownBy(() -> SupervisionFlywayMariaDbIntegrationTest.requireTemporaryMigrationUrl(
        "jdbc:mariadb://127.0.0.1:3306/pda_v78_it_abc"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pda_v79_it_");
    assertThatThrownBy(() -> SupervisionFlywayMariaDbIntegrationTest.requireTemporaryMigrationUrl(
        "jdbc:mariadb://127.0.0.1:3306/pda_v79_it_abc/another_database"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pda_v79_it_");
    assertThatThrownBy(() -> SupervisionFlywayMariaDbIntegrationTest.requireTemporaryMigrationUrl(
        "jdbc:mariadb://127.0.0.1:3306/pda_v79_it_abc?x=1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pda_v79_it_");
    assertThatThrownBy(() -> SupervisionFlywayMariaDbIntegrationTest.requireTemporaryMigrationUrl(
        "jdbc:mariadb://127.0.0.1:3306/pda_v79_it_abc#fragment"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pda_v79_it_");
  }

  @Test
  void acceptsAWellFormedDedicatedTemporaryMigrationUrlWithoutConnecting() {
    assertThatCode(() -> SupervisionFlywayMariaDbIntegrationTest.requireTemporaryMigrationUrl(
        "jdbc:mariadb://127.0.0.1:3306/pda_v79_it_abc"))
        .doesNotThrowAnyException();
  }

  @Test
  void migrationContractDefinesGovernanceStorageWithoutImagePayloads() throws Exception {
    String sql = migrationSql();
    String events = tableDefinition(sql, "supervision_events");
    assertThat(events).contains(
        "id bigint not null auto_increment",
        "event_id char(36) not null",
        "event_type varchar(64) not null",
        "operator_username varchar(64) null",
        "customer_phone varchar(32) null",
        "channel_code varchar(64) null",
        "channel_account varchar(255) null",
        "lead_source varchar(128) null",
        "assigned_keeper varchar(64) null",
        "scene varchar(64) null",
        "task_id char(36) null",
        "reply_session_id varchar(80) null",
        "reply_source varchar(64) null",
        "dedupe_key varchar(255) null",
        "generated_reply_snapshot text null",
        "copied_reply_snapshot text null",
        "metadata_json text not null",
        "occurred_at datetime(6) not null",
        "unique key uk_supervision_event_id (event_id)",
        "unique key uk_supervision_event_dedupe (dedupe_key)",
        "key idx_supervision_event_operator_time (operator_username, occurred_at)",
        "key idx_supervision_event_customer_time (customer_phone, occurred_at)",
        "key idx_supervision_event_channel_time (channel_code, occurred_at)");
    assertThat(events).doesNotContainPattern(
        "(?:^|[,\\s])(?:[a-z0-9_]*(?:image|base64|screenshot|ocr)[a-z0-9_]*)\\s+"
            + "(?:char|varchar|text|mediumtext|longtext|blob)");

    String metrics = tableDefinition(sql, "supervision_monthly_metric_snapshots");
    assertThat(metrics).contains(
        "id bigint not null auto_increment",
        "metric_month date not null",
        "dimension_type varchar(64) not null",
        "dimension_value varchar(255) not null",
        "metric_key varchar(100) not null",
        "numerator bigint not null",
        "denominator bigint not null",
        "ratio decimal(12,8) not null",
        "generated_at datetime(6) not null",
        "unique key uk_supervision_monthly_metric ( metric_month, dimension_type, dimension_value, metric_key )");

    assertConfigDefault(sql, "supervision.record_retention_days", "180");
    assertConfigDefault(sql, "supervision.technical_log_retention_days", "30");
    assertConfigDefault(sql, "supervision.processing_sla_minutes", "1440");
    assertConfigDefault(sql, "supervision.conversion_target_stages_json", "[]");
    assertConfigDefault(sql, "chat.expired_reply_task_retention_days", "3");
    assertConfigDefault(sql, "chat.unfinished_task_cap", "20");
    assertConfigDefault(sql, "chat.recent_task_display_cap", "30");
    assertConfigDefault(sql, "chat.recognition_concurrency", "4");
    assertThat(sql).contains("on duplicate key update description = values(description)");
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "SUPERVISION_FLYWAY_IT", matches = "true")
  void migratesSupervisionStorageAndGovernanceDefaults() throws Exception {
    String url = required("SUPERVISION_FLYWAY_URL");
    requireTemporaryMigrationUrl(url);
    String username = required("SUPERVISION_FLYWAY_USERNAME");
    String password = System.getenv().getOrDefault("SUPERVISION_FLYWAY_PASSWORD", "");
    Flyway flyway = Flyway.configure()
        .dataSource(url, username, password)
        .locations("classpath:db/migration")
        .load();

    MigrateResult first = flyway.migrate();
    MigrateResult second = flyway.migrate();

    assertThat(first.targetSchemaVersion).isEqualTo("79");
    assertThat(first.migrationsExecuted).isGreaterThan(0);
    assertThat(second.migrationsExecuted).isZero();
    try (var connection = DriverManager.getConnection(url, username, password);
         var statement = connection.createStatement()) {
      assertThat(queryCount(statement, """
          SELECT COUNT(*) FROM flyway_schema_history
          WHERE version='79' AND success=1
          """)).isEqualTo(1);
      assertThat(queryCount(statement, """
          SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME IN ('supervision_events', 'supervision_monthly_metric_snapshots')
          """)).isEqualTo(2);
      assertThat(queryCount(statement, """
          SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='supervision_events'
            AND COLUMN_NAME IN (
              'id', 'event_id', 'event_type', 'operator_username', 'customer_phone',
              'channel_code', 'channel_account', 'lead_source', 'assigned_keeper',
              'scene', 'task_id', 'reply_session_id', 'reply_source', 'dedupe_key',
              'generated_reply_snapshot', 'copied_reply_snapshot', 'metadata_json', 'occurred_at'
            )
          """)).isEqualTo(18);
      assertThat(queryCount(statement, """
          SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='supervision_events'
            AND (
              LOWER(COLUMN_NAME) LIKE '%image_base64%'
              OR LOWER(COLUMN_NAME) LIKE '%image%'
              OR LOWER(COLUMN_NAME) LIKE '%base64%'
              OR LOWER(COLUMN_NAME) LIKE '%screenshot%'
              OR LOWER(COLUMN_NAME) LIKE '%raw_image%'
              OR LOWER(COLUMN_NAME) LIKE '%ocr%'
            )
          """)).isZero();
      assertThat(queryCount(statement, """
          SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='supervision_events'
            AND INDEX_NAME IN (
              'uk_supervision_event_id', 'uk_supervision_event_dedupe',
              'idx_supervision_event_operator_time', 'idx_supervision_event_customer_time',
              'idx_supervision_event_channel_time'
            )
          """)).isEqualTo(5);
      assertThat(queryCount(statement, """
          SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA=DATABASE()
            AND (
              (TABLE_NAME='supervision_events' AND INDEX_NAME='idx_supervision_events_occurred_at')
              OR (TABLE_NAME='llm_call_logs' AND INDEX_NAME='idx_llm_call_logs_created_at')
              OR (TABLE_NAME='skill_call_logs' AND INDEX_NAME='idx_skill_call_logs_created_at')
              OR (TABLE_NAME='pending_reply_tasks'
                  AND INDEX_NAME='idx_pending_reply_tasks_status_finished_at')
            )
          """)).isEqualTo(4);
      assertThat(queryCount(statement, """
          SELECT COUNT(*) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='supervision_monthly_metric_snapshots'
            AND INDEX_NAME='uk_supervision_monthly_metric'
          """)).isEqualTo(4);
      assertThat(queryCount(statement, """
          SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='supervision_monthly_metric_snapshots'
            AND COLUMN_NAME IN (
              'id', 'metric_month', 'dimension_type', 'dimension_value', 'metric_key',
              'numerator', 'denominator', 'ratio', 'generated_at'
            )
          """)).isEqualTo(9);
      assertThat(queryCount(statement, """
          SELECT COUNT(*) FROM system_configs
          WHERE config_key IN (
            'supervision.record_retention_days',
            'supervision.technical_log_retention_days',
            'supervision.processing_sla_minutes',
            'supervision.conversion_target_stages_json',
            'chat.expired_reply_task_retention_days',
            'chat.unfinished_task_cap',
            'chat.recent_task_display_cap',
            'chat.recognition_concurrency',
            'chat.recognition_temp_root',
            'chat.recognition_temp_ttl_seconds',
            'chat.recognition_temp_max_total_bytes'
          )
            AND description IS NOT NULL
            AND description <> ''
          """)).isEqualTo(11);
      assertThat(queryString(statement, """
          SELECT config_value FROM system_configs
          WHERE config_key='supervision.record_retention_days'
          """)).isEqualTo("180");
      assertThat(queryString(statement, """
          SELECT config_value FROM system_configs
          WHERE config_key='supervision.conversion_target_stages_json'
          """)).isEqualTo("[]");
      assertThat(queryString(statement, """
          SELECT config_value FROM system_configs
          WHERE config_key='chat.unfinished_task_cap'
          """)).isEqualTo("20");
      assertThat(queryString(statement, """
          SELECT config_value FROM system_configs
          WHERE config_key='chat.recognition_temp_ttl_seconds'
          """)).isEqualTo("600");
      assertThat(queryString(statement, """
          SELECT config_value FROM system_configs
          WHERE config_key='chat.recognition_temp_root'
          """)).isEqualTo("active");
    }
  }

  private int queryCount(java.sql.Statement statement, String sql) throws Exception {
    try (var result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getInt(1);
    }
  }

  private String queryString(java.sql.Statement statement, String sql) throws Exception {
    try (var result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }

  private String required(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(key + " is required");
    }
    return value;
  }

  static void requireTemporaryMigrationUrl(String rawUrl) {
    String message = "SUPERVISION_FLYWAY_URL must target a pda_v79_it_ temporary database";
    if (rawUrl == null || rawUrl.isBlank() || !rawUrl.startsWith("jdbc:mariadb://")) {
      throw new IllegalArgumentException(message);
    }
    try {
      URI uri = URI.create(rawUrl.substring("jdbc:".length()));
      String database = uri.getPath();
      if (uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || database == null
          || !database.matches("/pda_v79_it_[A-Za-z0-9_]+")) {
        throw new IllegalArgumentException(message);
      }
    } catch (IllegalArgumentException ex) {
      if (message.equals(ex.getMessage())) {
        throw ex;
      }
      throw new IllegalArgumentException(message, ex);
    }
  }

  private String migrationSql() throws Exception {
    try (InputStream stream = getClass().getResourceAsStream(
        "/db/migration/V76__supervision_events_and_governance_config.sql")) {
      assertThat(stream).as("V76 migration resource").isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          .replaceAll("\\s+", " ")
          .toLowerCase(Locale.ROOT);
    }
  }

  private String tableDefinition(String sql, String tableName) {
    Matcher matcher = Pattern.compile(
        "create table if not exists " + Pattern.quote(tableName) + " \\( (.*?) \\) engine=",
        Pattern.DOTALL).matcher(sql);
    if (!matcher.find()) {
      throw new AssertionError("Missing CREATE TABLE statement for " + tableName);
    }
    return matcher.group(1);
  }

  private void assertConfigDefault(String sql, String key, String value) {
    assertThat(sql).containsPattern(
        "\\('" + Pattern.quote(key) + "', '" + Pattern.quote(value) + "', '[^']+'\\)");
  }
}
