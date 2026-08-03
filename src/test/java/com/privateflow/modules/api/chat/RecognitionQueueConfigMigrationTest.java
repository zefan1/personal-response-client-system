package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class RecognitionQueueConfigMigrationTest {

  @Test
  void definesOriginalTemporaryRecognitionStorageDefaultsInV78() throws Exception {
    String sql = migrationSql("V78__recognition_queue_config.sql");

    assertThat(sql).contains(
        "('chat.recognition_temp_root', 'uploads/temporary-recognition'",
        "('chat.recognition_temp_ttl_seconds', '600'",
        "('chat.recognition_temp_max_total_bytes', '104857600'",
        "on duplicate key update description = values(description)");
    assertThat(sql).doesNotContain("create table", "image_base64", "screenshot", "ocr");
  }

  @Test
  void upgradesOnlyTheLegacyDefaultDirectoryInV79() throws Exception {
    String sql = migrationSql("V79__constrain_temporary_recognition_directory.sql");

    assertThat(sql).contains(
        "update system_configs",
        "set config_value = 'active'",
        "temporary recognition image subdirectory below the application temporary root",
        "where config_key = 'chat.recognition_temp_root'",
        "and config_value = 'uploads/temporary-recognition'");
    assertThat(sql).doesNotContain("insert into", "on duplicate key update", "create table");
  }

  @Test
  void appliesV79ToLegacyDefaultAndPreservesAdministratorDirectory() throws Exception {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:recognition_queue_v79;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
    jdbcTemplate.execute("DROP TABLE IF EXISTS system_configs");
    jdbcTemplate.execute("CREATE TABLE system_configs (config_key VARCHAR(100) PRIMARY KEY, config_value TEXT, description TEXT)");
    jdbcTemplate.update("INSERT INTO system_configs (config_key, config_value, description) VALUES (?, ?, ?)",
        "chat.recognition_temp_root", "uploads/temporary-recognition", "legacy");

    jdbcTemplate.execute(rawMigrationSql("V79__constrain_temporary_recognition_directory.sql"));

    assertThat(jdbcTemplate.queryForObject(
        "SELECT config_value FROM system_configs WHERE config_key = ?", String.class,
        "chat.recognition_temp_root")).isEqualTo("active");
    jdbcTemplate.update("UPDATE system_configs SET config_value = ? WHERE config_key = ?",
        "team-a", "chat.recognition_temp_root");

    jdbcTemplate.execute(rawMigrationSql("V79__constrain_temporary_recognition_directory.sql"));

    assertThat(jdbcTemplate.queryForObject(
        "SELECT config_value FROM system_configs WHERE config_key = ?", String.class,
        "chat.recognition_temp_root")).isEqualTo("team-a");
  }

  private String migrationSql(String migrationName) throws Exception {
    return rawMigrationSql(migrationName)
        .replaceAll("\\s+", " ")
        .toLowerCase(Locale.ROOT);
  }

  private String rawMigrationSql(String migrationName) throws Exception {
    try (InputStream stream = getClass().getResourceAsStream(
        "/db/migration/" + migrationName)) {
      assertThat(stream).as(migrationName + " resource").isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
