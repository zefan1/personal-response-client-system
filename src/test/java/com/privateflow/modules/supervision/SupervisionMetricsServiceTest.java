package com.privateflow.modules.supervision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.Scheduled;

class SupervisionMetricsServiceTest {

  private static final LocalDateTime FROM = LocalDateTime.of(2026, 7, 1, 0, 0);
  private static final LocalDateTime TO = LocalDateTime.of(2026, 8, 1, 0, 0);

  private JdbcTemplate jdbcTemplate;
  private SupervisionConfig config;
  private SupervisionMetricsService service;

  @BeforeEach
  void setUp() {
    jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:supervision_metrics_service_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        ""));
    createSchema();
    SystemConfigRepository configRepository = new SystemConfigRepository(jdbcTemplate);
    putConfig("supervision.processing_sla_minutes", "60");
    putConfig("supervision.conversion_target_stages_json", "[\"SIGNED\"]");
    config = new SupervisionConfig(configRepository);
    config.refresh();
    service = new SupervisionMetricsService(
        new SupervisionMetricsRepository(jdbcTemplate),
        config,
        Clock.fixed(Instant.parse("2026-07-23T04:20:00Z"), ZoneOffset.UTC));
    seedCoreWorkflow();
  }

  @AfterEach
  void clearAuthContext() {
    AuthContext.clear();
  }

  @Test
  void reportsAuditableNumeratorsDenominatorsRatesAndLabelsForAnAdministrator() {
    AuthContext.set(new AuthUser("admin", "Admin", Role.ADMIN, null));

    Map<String, SupervisionMetric> report = service.report(query());

    assertMetric(report.get("AI_USAGE_RATE"), 1, 2, 0.5,
        "AI copied customers", "AI generated customers", true);
    assertMetric(report.get("AI_COVERAGE"), 2, 4, 0.5,
        "Customers with AI replies", "Customers processed by recognition", true);
    assertMetric(report.get("PROCESSING_EFFICIENCY"), 1, 4, 0.25,
        "Customers handled within SLA", "Customers processed by recognition", true);
    assertMetric(report.get("EMPLOYEE_CONVERSION"), 1, 3, 1.0 / 3,
        "Assigned customers from the selected period currently at configured target stages",
        "Assigned customers from the selected period", true);

    insertEvent("REPLY_COPIED", "alice", "13800000002", "WECHAT", "ads-form",
        FROM.plusMinutes(40));
    report = service.report(query());
    assertMetric(report.get("AI_REPLY_CONVERSION"), 1, 2, 0.5,
        "AI copied customers currently at configured target stages", "AI copied customers", true);
  }

  @Test
  void reportsZeroConversionNumeratorsWithoutPretendingThatTargetsAreConfigured() {
    AuthContext.set(new AuthUser("admin", "Admin", Role.ADMIN, null));
    putConfig("supervision.conversion_target_stages_json", "[]");
    SupervisionConfig config = new SupervisionConfig(new SystemConfigRepository(jdbcTemplate));
    config.refresh();
    service = new SupervisionMetricsService(
        new SupervisionMetricsRepository(jdbcTemplate),
        config,
        Clock.fixed(Instant.parse("2026-07-23T04:20:00Z"), ZoneOffset.UTC));

    Map<String, SupervisionMetric> report = service.report(query());

    assertMetric(report.get("EMPLOYEE_CONVERSION"), 0, 3, 0.0,
        "Assigned customers from the selected period currently at configured target stages",
        "Assigned customers from the selected period", false);
    assertMetric(report.get("AI_REPLY_CONVERSION"), 0, 1, 0.0,
        "AI copied customers currently at configured target stages", "AI copied customers", false);
  }

  @Test
  void rejectsUnauthenticatedAndNonAdminMetricReads() {
    assertThatThrownBy(() -> service.report(query()))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getErrorCode())
        .isEqualTo(ApiErrorCodes.AUTH_FAILED);

    AuthContext.set(new AuthUser("keeper", "Keeper", Role.KEEPER, null));
    assertThatThrownBy(() -> service.report(query()))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getErrorCode())
        .isEqualTo(ApiErrorCodes.FORBIDDEN);
  }

  @Test
  void upsertsPreviousCompleteMonthMetricsForAllAndEachAvailableDimension() {
    service.snapshotCurrentMonthAt(LocalDateTime.of(2026, 8, 1, 4, 20));

    assertThat(snapshotCount("ALL", "ALL", "AI_USAGE_RATE")).isEqualTo(1);
    assertThat(snapshotCount("OPERATOR", "alice", "AI_USAGE_RATE")).isEqualTo(1);
    assertThat(snapshotCount("CHANNEL", "WECHAT", "AI_USAGE_RATE")).isEqualTo(1);
    assertThat(snapshotCount("LEAD_SOURCE", "ads-form", "AI_USAGE_RATE")).isEqualTo(1);
    assertThat(snapshotMetricMonth("ALL", "ALL", "AI_USAGE_RATE"))
        .isEqualTo(java.sql.Date.valueOf("2026-07-01"));

    insertEvent("REPLY_GENERATED", "alice", "13800000003", "WECHAT", "ads-form",
        LocalDateTime.of(2026, 7, 31, 23, 0));
    insertEvent("REPLY_GENERATED", "alice", "13800000005", "WECHAT", "ads-form",
        LocalDateTime.of(2026, 8, 1, 0, 10));
    service.snapshotCurrentMonthAt(LocalDateTime.of(2026, 8, 1, 4, 20));

    assertThat(snapshotCount("ALL", "ALL", "AI_USAGE_RATE")).isEqualTo(1);
    assertThat(snapshotNumerator("ALL", "ALL", "AI_USAGE_RATE")).isEqualTo(1L);
    assertThat(snapshotDenominator("ALL", "ALL", "AI_USAGE_RATE")).isEqualTo(3L);
  }

  @Test
  void snapshotsAreScheduledForTheFirstDayAtShanghai0420() throws NoSuchMethodException {
    Scheduled scheduled = SupervisionMetricsService.class
        .getMethod("snapshotCurrentMonth")
        .getAnnotation(Scheduled.class);

    assertThat(scheduled.cron()).isEqualTo("0 20 4 1 * *");
    assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
  }

  @Test
  void injectsTheSharedReplyTaskClockInSpringRuntime() throws NoSuchMethodException {
    assertThat(SupervisionMetricsService.class.getDeclaredConstructor(
        SupervisionMetricsRepository.class,
        SupervisionConfig.class,
        com.privateflow.modules.api.chat.ReplyTaskClock.class)
        .isAnnotationPresent(Autowired.class)).isTrue();
  }

  @Test
  void scheduledSnapshotUsesShanghaiMonthEvenWhenTheRuntimeClockIsUtc() {
    SupervisionMetricsService utcRuntimeService = new SupervisionMetricsService(
        new SupervisionMetricsRepository(jdbcTemplate),
        config,
        Clock.fixed(Instant.parse("2026-07-31T16:30:00Z"), ZoneOffset.UTC));

    utcRuntimeService.snapshotCurrentMonth();

    assertThat(snapshotMetricMonth("ALL", "ALL", "AI_USAGE_RATE"))
        .isEqualTo(java.sql.Date.valueOf("2026-07-01"));
  }

  private void assertMetric(
      SupervisionMetric metric,
      long numerator,
      long denominator,
      double rate,
      String numeratorLabel,
      String denominatorLabel,
      boolean targetConfigured) {
    assertThat(metric).isNotNull();
    assertThat(metric.numerator()).isEqualTo(numerator);
    assertThat(metric.denominator()).isEqualTo(denominator);
    assertThat(metric.rate()).isEqualTo(rate);
    assertThat(metric.numeratorLabel()).isEqualTo(numeratorLabel);
    assertThat(metric.denominatorLabel()).isEqualTo(denominatorLabel);
    assertThat(metric.conversionTargetConfigured()).isEqualTo(targetConfigured);
  }

  private SupervisionMetricsQuery query() {
    return new SupervisionMetricsQuery(FROM, TO, "alice", "WECHAT", "ads-form");
  }

  private void createSchema() {
    jdbcTemplate.execute("""
        CREATE TABLE system_configs (
          config_key VARCHAR(100) PRIMARY KEY,
          config_value VARCHAR(255) NOT NULL,
          description VARCHAR(255)
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE customers (
          phone VARCHAR(32) PRIMARY KEY,
          assigned_keeper VARCHAR(64),
          source_channel VARCHAR(64),
          source_table VARCHAR(128),
          customer_stage VARCHAR(64),
          created_at TIMESTAMP NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE supervision_events (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          event_type VARCHAR(64) NOT NULL,
          operator_username VARCHAR(64),
          customer_phone VARCHAR(32),
          channel_code VARCHAR(64),
          lead_source VARCHAR(128),
          occurred_at TIMESTAMP NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE supervision_monthly_metric_snapshots (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          metric_month DATE NOT NULL,
          dimension_type VARCHAR(64) NOT NULL,
          dimension_value VARCHAR(255) NOT NULL,
          metric_key VARCHAR(100) NOT NULL,
          numerator BIGINT NOT NULL,
          denominator BIGINT NOT NULL,
          ratio DECIMAL(12,8) NOT NULL,
          generated_at TIMESTAMP NOT NULL,
          UNIQUE (metric_month, dimension_type, dimension_value, metric_key)
        )
        """);
  }

  private void seedCoreWorkflow() {
    insertCustomer("13800000001", "alice", "WECHAT", "ads-form", "SIGNED");
    insertCustomer("13800000002", "alice", "WECHAT", "ads-form", "FOLLOW_UP");
    insertCustomer("13800000003", "alice", "WECHAT", "ads-form", "FOLLOW_UP");
    insertCustomer("13800000004", "bob", "WECHAT", "ads-form", "SIGNED");
    for (int index = 1; index <= 4; index++) {
      insertEvent("RECOGNITION_PROCESSED", "alice", "1380000000" + index, "WECHAT", "ads-form",
          FROM.plusHours(1));
    }
    insertEvent("REPLY_GENERATED", "alice", "13800000001", "WECHAT", "ads-form",
        FROM.plusHours(1).plusMinutes(20));
    insertEvent("REPLY_COPIED", "alice", "13800000001", "WECHAT", "ads-form",
        FROM.plusHours(1).plusMinutes(21));
    insertEvent("REPLY_GENERATED", "alice", "13800000002", "WECHAT", "ads-form",
        FROM.plusHours(4));
  }

  private void insertCustomer(
      String phone,
      String keeper,
      String channel,
      String source,
      String stage) {
    jdbcTemplate.update("""
        INSERT INTO customers (
          phone, assigned_keeper, source_channel, source_table, customer_stage, created_at
        ) VALUES (?, ?, ?, ?, ?, ?)
        """,
        phone,
        keeper,
        channel,
        source,
        stage,
        Timestamp.valueOf(FROM.plusDays(1)));
  }

  private void insertEvent(
      String type,
      String operator,
      String phone,
      String channel,
      String source,
      LocalDateTime occurredAt) {
    jdbcTemplate.update("""
        INSERT INTO supervision_events (
          event_type, operator_username, customer_phone, channel_code, lead_source, occurred_at
        ) VALUES (?, ?, ?, ?, ?, ?)
        """,
        type,
        operator,
        phone,
        channel,
        source,
        Timestamp.valueOf(occurredAt));
  }

  private void putConfig(String key, String value) {
    jdbcTemplate.update("""
        MERGE INTO system_configs (config_key, config_value, description)
        KEY (config_key) VALUES (?, ?, ?)
        """, key, value, key);
  }

  private int snapshotCount(String type, String value, String key) {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM supervision_monthly_metric_snapshots
        WHERE dimension_type = ? AND dimension_value = ? AND metric_key = ?
        """, Integer.class, type, value, key);
    return count == null ? 0 : count;
  }

  private long snapshotNumerator(String type, String value, String key) {
    Long valueResult = jdbcTemplate.queryForObject("""
        SELECT numerator FROM supervision_monthly_metric_snapshots
        WHERE dimension_type = ? AND dimension_value = ? AND metric_key = ?
        """, Long.class, type, value, key);
    return valueResult == null ? 0L : valueResult;
  }

  private long snapshotDenominator(String type, String value, String key) {
    Long valueResult = jdbcTemplate.queryForObject("""
        SELECT denominator FROM supervision_monthly_metric_snapshots
        WHERE dimension_type = ? AND dimension_value = ? AND metric_key = ?
        """, Long.class, type, value, key);
    return valueResult == null ? 0L : valueResult;
  }

  private java.sql.Date snapshotMetricMonth(String type, String value, String key) {
    return jdbcTemplate.queryForObject("""
        SELECT metric_month FROM supervision_monthly_metric_snapshots
        WHERE dimension_type = ? AND dimension_value = ? AND metric_key = ?
        """, java.sql.Date.class, type, value, key);
  }
}
