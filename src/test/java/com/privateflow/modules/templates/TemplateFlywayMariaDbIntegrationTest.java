package com.privateflow.modules.templates;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TemplateFlywayMariaDbIntegrationTest {

  @Test
  void migrationDefinesTemplatePromotionStorageWithoutScreenshotOrOcrPayloads() throws Exception {
    String sql = migrationSql();

    assertThat(tableDefinition(sql, "personal_templates")).contains(
        "owner_username varchar(64) not null",
        "title varchar(120) not null",
        "body text not null",
        "labels_json text not null",
        "usage_count bigint not null default 0",
        "key idx_personal_template_owner_time (owner_username, updated_at)");
    String candidates = tableDefinition(sql, "template_promotion_candidates");
    assertThat(candidates).contains(
        "personal_template_id bigint not null",
        "original_ai_reply text not null",
        "edited_body text not null",
        "metadata_json text not null",
        "status varchar(32) not null default 'candidate'",
        "key idx_candidate_status_time (status, created_at)");
    assertThat(sql).doesNotContainPattern(
        "(?:^|[,\\s])(?:[a-z0-9_]*(?:image|base64|screenshot|ocr)[a-z0-9_]*)\\s+"
            + "(?:char|varchar|text|mediumtext|longtext|blob)");
    assertThat(tableDefinition(sql, "team_template_publications")).contains(
        "unique key uk_team_template_publication_candidate (candidate_id)",
        "unique key uk_team_template_publication_quick_search_item (quick_search_item_id)");
  }

  private String migrationSql() throws Exception {
    try (InputStream stream = getClass().getClassLoader()
        .getResourceAsStream("db/migration/V80__personal_templates_and_promotions.sql")) {
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
