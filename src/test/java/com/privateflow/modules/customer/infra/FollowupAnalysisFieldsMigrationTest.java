package com.privateflow.modules.customer.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class FollowupAnalysisFieldsMigrationTest {

  @Test
  void migrationAddsAllLocalAnalysisFields() throws Exception {
    String sql = Files.readString(
        Path.of("src/main/resources/db/migration/V83__followup_analysis_profile_fields.sql"),
        StandardCharsets.UTF_8);

    assertThat(sql)
        .contains("internal_note")
        .contains("customer_profile_summary")
        .contains("first_tracking_capture")
        .contains("second_tracking_capture")
        .contains("third_tracking_capture");
  }

  @Test
  void migrationAddsPersistentAnalysisRetryQueue() throws Exception {
    String sql = Files.readString(
        Path.of("src/main/resources/db/migration/V84__pending_followup_analysis_retry.sql"),
        StandardCharsets.UTF_8);

    assertThat(sql)
        .contains("pending_followup_analyses")
        .contains("request_key")
        .contains("next_retry_at")
        .contains("retry_count");
  }

  @Test
  void migrationEnablesFollowupAnalysisWithoutOverwritingAdministratorChoice() throws Exception {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:followup_analysis_config_v85;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
    jdbcTemplate.execute("DROP TABLE IF EXISTS system_configs");
    jdbcTemplate.execute("""
        CREATE TABLE system_configs (
          config_key VARCHAR(100) PRIMARY KEY,
          config_value TEXT NOT NULL,
          description TEXT
        )
        """);

    jdbcTemplate.execute(migrationSql("V85__enable_followup_analysis.sql"));
    assertThat(jdbcTemplate.queryForObject(
        "SELECT config_value FROM system_configs WHERE config_key = ?",
        String.class,
        "llm.followup_analysis.enabled")).isEqualTo("true");

    jdbcTemplate.update(
        "UPDATE system_configs SET config_value = ? WHERE config_key = ?",
        "false",
        "llm.followup_analysis.enabled");
    jdbcTemplate.execute(migrationSql("V85__enable_followup_analysis.sql"));

    assertThat(jdbcTemplate.queryForObject(
        "SELECT config_value FROM system_configs WHERE config_key = ?",
        String.class,
        "llm.followup_analysis.enabled")).isEqualTo("false");
  }

  private String migrationSql(String migrationName) throws Exception {
    return Files.readString(
        Path.of("src/main/resources/db/migration", migrationName),
        StandardCharsets.UTF_8);
  }
}
