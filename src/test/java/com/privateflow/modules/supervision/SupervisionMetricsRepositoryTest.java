package com.privateflow.modules.supervision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SupervisionMetricsRepositoryTest {

  private static final LocalDateTime FROM = LocalDateTime.of(2026, 7, 1, 0, 0);
  private static final LocalDateTime TO = LocalDateTime.of(2026, 8, 1, 0, 0);

  private JdbcTemplate jdbcTemplate;
  private SupervisionMetricsRepository repository;

  @BeforeEach
  void setUp() {
    jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:supervision_metrics_repository_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        ""));
    createSchema();
    repository = new SupervisionMetricsRepository(jdbcTemplate);
  }

  @Test
  void calculatesEventMetricsWithTheSameOperatorChannelAndLeadSourceFilters() {
    seedCoreWorkflow();
    SupervisionMetricsQuery query = query("alice", "WECHAT", "ads-form");

    assertThat(repository.aiUsageRate(query))
        .isEqualTo(new SupervisionMetricsRepository.Counts(1, 2));
    assertThat(repository.aiCoverage(query))
        .isEqualTo(new SupervisionMetricsRepository.Counts(2, 4));
    assertThat(repository.processingEfficiency(query, 60))
        .isEqualTo(new SupervisionMetricsRepository.Counts(1, 4));

    insertEvent("REPLY_GENERATED", "bob", "13800000005", "WECHAT", "ads-form",
        FROM.plusHours(2));
    insertEvent("REPLY_COPIED", "alice", "13800000006", "DOUYIN", "ads-form",
        FROM.plusHours(2));
    insertEvent("REPLY_COPIED", "alice", "13800000007", "WECHAT", "offline",
        FROM.plusHours(2));

    assertThat(repository.aiUsageRate(query))
        .isEqualTo(new SupervisionMetricsRepository.Counts(1, 2));
    assertThat(repository.aiCoverage(query))
        .isEqualTo(new SupervisionMetricsRepository.Counts(2, 4));
  }

  @Test
  void limitsUsageAndCoverageNumeratorsToTheirDenominatorCustomerBaselines() {
    seedCoreWorkflow();
    SupervisionMetricsQuery query = query("alice", "WECHAT", "ads-form");
    insertEvent("REPLY_COPIED", "alice", "13800000005", "WECHAT", "ads-form",
        FROM.plusHours(2));
    insertEvent("REPLY_COPIED", "alice", "13800000006", "WECHAT", "ads-form",
        FROM.plusHours(2));

    assertThat(repository.aiUsageRate(query))
        .isEqualTo(new SupervisionMetricsRepository.Counts(1, 2));
    assertThat(repository.aiCoverage(query))
        .isEqualTo(new SupervisionMetricsRepository.Counts(2, 4));
  }

  @Test
  void calculatesConversionMetricsFromDynamicTargetStagesAndKeepsZeroNumeratorsHonest() {
    seedCoreWorkflow();
    insertCustomer("13800000008", null, "WECHAT", "ads-form", "SIGNED");
    SupervisionMetricsQuery query = query("alice", "WECHAT", "ads-form");

    assertThat(repository.employeeConversion(query, List.of("SIGNED")))
        .isEqualTo(new SupervisionMetricsRepository.Counts(1, 3));
    assertThat(repository.employeeConversion(
        new SupervisionMetricsQuery(FROM, TO, null, "WECHAT", "ads-form"),
        List.of("SIGNED")))
        .isEqualTo(new SupervisionMetricsRepository.Counts(2, 4));

    insertEvent("REPLY_COPIED", "alice", "13800000002", "WECHAT", "ads-form",
        FROM.plusMinutes(40));
    assertThat(repository.aiAssociatedConversion(query, List.of("SIGNED")))
        .isEqualTo(new SupervisionMetricsRepository.Counts(1, 2));

    assertThat(repository.employeeConversion(query, List.of()))
        .isEqualTo(new SupervisionMetricsRepository.Counts(0, 3));
    assertThat(repository.aiAssociatedConversion(query, List.of()))
        .isEqualTo(new SupervisionMetricsRepository.Counts(0, 2));
  }

  @Test
  void returnsZeroRatesWhenNoCustomerCanEnterTheDenominator() {
    SupervisionMetricsQuery query = query("alice", "WECHAT", "ads-form");

    assertThat(repository.aiUsageRate(query))
        .isEqualTo(new SupervisionMetricsRepository.Counts(0, 0));
    assertThat(repository.aiCoverage(query))
        .isEqualTo(new SupervisionMetricsRepository.Counts(0, 0));
    assertThat(repository.processingEfficiency(query, 60))
        .isEqualTo(new SupervisionMetricsRepository.Counts(0, 0));
    assertThat(repository.employeeConversion(query, List.of("SIGNED")))
        .isEqualTo(new SupervisionMetricsRepository.Counts(0, 0));
    assertThat(repository.aiAssociatedConversion(query, List.of("SIGNED")))
        .isEqualTo(new SupervisionMetricsRepository.Counts(0, 0));
  }

  @Test
  void rejectsAnInvertedOrIncompleteDateRangeBeforeBuildingSql() {
    assertThatThrownBy(() -> new SupervisionMetricsQuery(TO, FROM, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("date range");
    assertThatThrownBy(() -> new SupervisionMetricsQuery(FROM, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("date range");
    assertThatThrownBy(() -> new SupervisionMetricsQuery(FROM, FROM, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("date range");
  }

  private void seedCoreWorkflow() {
    insertCustomer("13800000001", "alice", "WECHAT", "ads-form", "SIGNED");
    insertCustomer("13800000002", "alice", "WECHAT", "ads-form", "FOLLOW_UP");
    insertCustomer("13800000003", "alice", "WECHAT", "ads-form", "FOLLOW_UP");
    insertCustomer("13800000004", "bob", "WECHAT", "ads-form", "SIGNED");

    for (int index = 1; index <= 4; index++) {
      insertEvent("PENDING_ENTERED", "alice", "1380000000" + index, "WECHAT", "ads-form",
          FROM.plusHours(1));
    }
    insertEvent("REPLY_GENERATED", "alice", "13800000001", "WECHAT", "ads-form",
        FROM.plusHours(1).plusMinutes(20));
    insertEvent("REPLY_COPIED", "alice", "13800000001", "WECHAT", "ads-form",
        FROM.plusHours(1).plusMinutes(21));
    insertEvent("REPLY_GENERATED", "alice", "13800000002", "WECHAT", "ads-form",
        FROM.plusHours(4));
  }

  private SupervisionMetricsQuery query(String operator, String channel, String source) {
    return new SupervisionMetricsQuery(FROM, TO, operator, channel, source);
  }

  private void createSchema() {
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
  }

  private void insertCustomer(
      String phone,
      String assignedKeeper,
      String channel,
      String source,
      String stage) {
    jdbcTemplate.update("""
        INSERT INTO customers (
          phone, assigned_keeper, source_channel, source_table, customer_stage, created_at
        ) VALUES (?, ?, ?, ?, ?, ?)
        """,
        phone,
        assignedKeeper,
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
}
