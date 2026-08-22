package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RecognitionJobRestartRecoveryMigrationTest {

  @Test
  void storesOnlySafeTaskMetadataAndNoScreenshotOrRecognitionPayload() throws Exception {
    try (InputStream stream = getClass().getResourceAsStream(
        "/db/migration/V99__recognition_job_restart_recovery.sql")) {
      assertThat(stream).isNotNull();
      String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

      assertThat(sql).contains("job_id", "username", "reply_session_id", "status", "error_code");
      assertThat(sql).doesNotContain("image_base64", "image_token", "recognition_payload", "response_payload");
    }
  }
}
