package com.privateflow.modules.image.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ImageTimeoutMigrationTest {

  @Test
  void upgradesUnsafeTimeoutsAndPreservesLongerAdministratorValues() throws Exception {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:image_timeout_v82;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
    jdbcTemplate.execute("DROP TABLE IF EXISTS system_configs");
    jdbcTemplate.execute("CREATE TABLE system_configs (config_key VARCHAR(100) PRIMARY KEY, config_value TEXT, description TEXT)");
    jdbcTemplate.update("INSERT INTO system_configs (config_key, config_value, description) VALUES (?, ?, ?)",
        "image.timeout_ms", "5000", "legacy");

    jdbcTemplate.execute(migrationSql());

    assertThat(jdbcTemplate.queryForObject(
        "SELECT config_value FROM system_configs WHERE config_key = ?", String.class,
        "image.timeout_ms")).isEqualTo("15000");
    jdbcTemplate.update("UPDATE system_configs SET config_value = ? WHERE config_key = ?",
        "30000", "image.timeout_ms");

    jdbcTemplate.execute(migrationSql());

    assertThat(jdbcTemplate.queryForObject(
        "SELECT config_value FROM system_configs WHERE config_key = ?", String.class,
        "image.timeout_ms")).isEqualTo("30000");
  }

  private String migrationSql() throws Exception {
    try (InputStream stream = getClass().getResourceAsStream(
        "/db/migration/V82__increase_image_recognition_timeout.sql")) {
      assertThat(stream).as("V82 image timeout migration").isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
