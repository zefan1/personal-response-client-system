package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class RecognitionQueueConfigMigrationTest {

  @Test
  void definesOnlyTemporaryRecognitionStorageConfiguration() throws Exception {
    String sql = migrationSql();

    assertThat(sql).contains(
        "('chat.recognition_temp_root', 'uploads/temporary-recognition'",
        "('chat.recognition_temp_ttl_seconds', '600'",
        "('chat.recognition_temp_max_total_bytes', '104857600'",
        "on duplicate key update description = values(description)");
    assertThat(sql).doesNotContain("create table", "image_base64", "screenshot", "ocr");
  }

  private String migrationSql() throws Exception {
    try (InputStream stream = getClass().getResourceAsStream(
        "/db/migration/V78__recognition_queue_config.sql")) {
      assertThat(stream).as("V78 migration resource").isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          .replaceAll("\\s+", " ")
          .toLowerCase(Locale.ROOT);
    }
  }
}
