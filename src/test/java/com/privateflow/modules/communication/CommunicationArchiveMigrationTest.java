package com.privateflow.modules.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CommunicationArchiveMigrationTest {

  @Test
  void migrationDefinesPermanentTextArchiveAndVersionedSummaries() throws Exception {
    String sql = migrationSql();

    assertThat(tableDefinition(sql, "communication_recognition_batches")).contains(
        "batch_id char(36) not null",
        "raw_text mediumtext not null",
        "customer_id bigint null",
        "association_status varchar(24) not null",
        "recognized_at datetime(6) not null");
    assertThat(tableDefinition(sql, "communication_messages")).contains(
        "original_text text not null",
        "current_text text not null",
        "sender_role varchar(24) not null",
        "message_time datetime(6) not null",
        "time_estimated tinyint not null",
        "dedupe_fingerprint char(64) not null");
    assertThat(tableDefinition(sql, "communication_platform_identities")).contains(
        "platform_code varchar(32) not null",
        "normalized_identifier varchar(255) not null",
        "customer_id bigint not null",
        "unique key uk_communication_identity_customer (platform_code, normalized_identifier, customer_id)");
    assertThat(tableDefinition(sql, "communication_message_corrections")).contains(
        "message_id bigint not null",
        "previous_text text not null",
        "corrected_text text not null",
        "corrected_by varchar(64) not null",
        "corrected_at datetime(6) not null");
    assertThat(tableDefinition(sql, "communication_summary_versions")).contains(
        "customer_id bigint not null",
        "version_no int not null",
        "summary_text text not null",
        "last_message_id bigint not null",
        "unique key uk_communication_summary_version (customer_id, version_no)");
    assertThat(tableDefinition(sql, "communication_summary_states")).contains(
        "customer_id bigint not null",
        "status varchar(24) not null",
        "last_summarized_message_id bigint null",
        "retry_count int not null default 0",
        "next_retry_at datetime(6) null");
    assertThat(tableDefinition(sql, "communication_pending_task_links")).contains(
        "task_id char(36) not null",
        "batch_id char(36) not null",
        "unique key uk_communication_pending_batch (batch_id)");

    assertThat(sql).doesNotContain("on delete cascade");
    assertThat(sql).doesNotContainPattern("(?i)\\b(?:blob|mediumblob|longblob)\\b");
  }

  private String migrationSql() throws Exception {
    try (InputStream stream = getClass().getClassLoader()
        .getResourceAsStream("db/migration/V86__communication_archive.sql")) {
      assertThat(stream).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
    }
  }

  private String tableDefinition(String sql, String table) {
    int start = sql.indexOf("create table " + table);
    assertThat(start).isGreaterThanOrEqualTo(0);
    int end = sql.indexOf(") engine=", start);
    assertThat(end).isGreaterThan(start);
    return sql.substring(start, end + 1).replaceAll("\\s+", " ");
  }
}
