package com.privateflow.modules.supervision;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class RetentionCleanupIndexMigrationTest {

  @Test
  void migrationAddsOnlyTheRetentionCleanupIndexes() throws Exception {
    String sql = migrationSql();

    assertThat(sql).contains(
        "create index idx_supervision_events_occurred_at on supervision_events (occurred_at)",
        "create index idx_llm_call_logs_created_at on llm_call_logs (created_at)",
        "create index idx_skill_call_logs_created_at on skill_call_logs (created_at)",
        "create index idx_pending_reply_tasks_status_finished_at on pending_reply_tasks (status, finished_at)");
  }

  private String migrationSql() throws Exception {
    try (InputStream stream = getClass().getResourceAsStream(
        "/db/migration/V77__add_retention_cleanup_indexes.sql")) {
      assertThat(stream).as("V77 migration resource").isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          .replaceAll("\\s+", " ")
          .trim()
          .toLowerCase(Locale.ROOT);
    }
  }
}
