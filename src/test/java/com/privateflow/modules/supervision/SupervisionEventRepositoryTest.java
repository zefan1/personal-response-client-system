package com.privateflow.modules.supervision;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SupervisionEventRepositoryTest {

  private static final LocalDateTime FROM = LocalDateTime.of(2026, 7, 1, 0, 0);
  private JdbcTemplate jdbcTemplate;
  private SupervisionEventRepository repository;

  @BeforeEach
  void setUp() {
    jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:supervision_event_repository_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        ""));
    jdbcTemplate.execute("""
        CREATE TABLE supervision_events (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          event_type VARCHAR(64) NOT NULL,
          operator_username VARCHAR(64),
          customer_phone VARCHAR(32),
          channel_code VARCHAR(64),
          lead_source VARCHAR(128),
          assigned_keeper VARCHAR(64),
          scene VARCHAR(64),
          reply_source VARCHAR(32),
          generated_reply_snapshot TEXT,
          copied_reply_snapshot TEXT,
          occurred_at TIMESTAMP NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE customers (
          phone VARCHAR(32) PRIMARY KEY,
          assigned_keeper VARCHAR(64),
          source_channel VARCHAR(64),
          source_table VARCHAR(128),
          customer_stage VARCHAR(64)
        )
        """);
    repository = new SupervisionEventRepository(jdbcTemplate, new ObjectMapper());
  }

  @Test
  void masksSeparatedPhoneNumbersInReplyPreview() {
    jdbcTemplate.update("""
        INSERT INTO supervision_events (
          event_type, operator_username, customer_phone, channel_code, lead_source,
          copied_reply_snapshot, occurred_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        "REPLY_COPIED",
        "alice",
        "13800138000",
        "WECHAT",
        "ads-form",
        "请联系 138-0013-8000，备用号码 139 0014 9000。",
        Timestamp.valueOf(FROM.plusHours(1)));

    SupervisionEventPage page = repository.findPage(new SupervisionEventQuery(
        FROM,
        FROM.plusDays(1),
        null,
        null,
        null,
        SupervisionEventType.REPLY_COPIED,
        1,
        20));

    assertThat(page.items()).singleElement()
        .extracting(SupervisionEventView::replyPreview)
        .isEqualTo("请联系 138****8000，备用号码 139****9000。");
  }

  @Test
  void filtersEventsAndLoadsAllMetadataDimensionsFromTheDatabase() {
    insertEvent("alice", "13800138001", "WECHAT", "ads-form", "REPLY_COPIED", FROM.plusHours(1));
    insertEvent("bob", "13900149002", "DOUYIN", "store-referral", "REPLY_COPIED", FROM.plusHours(2));
    insertCustomer("13700127003", "carol", "XIAOHONGSHU", "landing-page", "已成交");

    SupervisionEventPage page = repository.findPage(new SupervisionEventQuery(
        FROM,
        FROM.plusDays(1),
        "alice",
        "WECHAT",
        "ads-form",
        SupervisionEventType.REPLY_COPIED,
        1,
        20));
    SupervisionMetadata metadata = repository.metadata();

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items()).singleElement()
        .extracting(SupervisionEventView::customerPhoneMasked)
        .isEqualTo("138****8001");
    assertThat(metadata.operators()).containsExactly("alice", "bob", "carol");
    assertThat(metadata.channels()).containsExactly("DOUYIN", "WECHAT", "XIAOHONGSHU");
    assertThat(metadata.leadSources()).containsExactly("ads-form", "landing-page", "store-referral");
    assertThat(metadata.customerStages()).containsExactly("已成交");
    assertThat(metadata.eventTypes()).containsExactly("REPLY_COPIED");
  }

  @Test
  void returnsTheRequestedPageInNewestFirstOrder() {
    insertEvent("alice", "13800138001", "WECHAT", "ads-form", "REPLY_COPIED", FROM.plusHours(1));
    insertEvent("alice", "13800138002", "WECHAT", "ads-form", "REPLY_COPIED", FROM.plusHours(2));
    insertEvent("alice", "13800138003", "WECHAT", "ads-form", "REPLY_COPIED", FROM.plusHours(3));

    SupervisionEventPage page = repository.findPage(new SupervisionEventQuery(
        FROM,
        FROM.plusDays(1),
        null,
        null,
        null,
        SupervisionEventType.REPLY_COPIED,
        2,
        1));

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.items()).singleElement()
        .extracting(SupervisionEventView::customerPhoneMasked)
        .isEqualTo("138****8002");
  }

  private void insertEvent(
      String operator,
      String phone,
      String channel,
      String source,
      String eventType,
      LocalDateTime occurredAt) {
    jdbcTemplate.update("""
        INSERT INTO supervision_events (
          event_type, operator_username, customer_phone, channel_code, lead_source, occurred_at
        ) VALUES (?, ?, ?, ?, ?, ?)
        """,
        eventType,
        operator,
        phone,
        channel,
        source,
        Timestamp.valueOf(occurredAt));
  }

  private void insertCustomer(
      String phone,
      String operator,
      String channel,
      String source,
      String stage) {
    jdbcTemplate.update("""
        INSERT INTO customers (phone, assigned_keeper, source_channel, source_table, customer_stage)
        VALUES (?, ?, ?, ?, ?)
        """,
        phone,
        operator,
        channel,
        source,
        stage);
  }
}
