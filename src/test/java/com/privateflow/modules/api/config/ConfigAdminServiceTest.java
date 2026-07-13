package com.privateflow.modules.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.ai.PromptVersionService;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.security.SecretCipher;
import com.privateflow.modules.api.ws.WsPushService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ConfigAdminServiceTest {

  private JdbcTemplate jdbcTemplate;
  private SecretCipher secretCipher;
  private ConfigAdminService service;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:config_admin;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS system_configs");
    jdbcTemplate.execute("""
        CREATE TABLE system_configs (
          config_key VARCHAR(100) PRIMARY KEY,
          config_value TEXT NOT NULL,
          description VARCHAR(500),
          updated_by VARCHAR(50),
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    insertConfig("skill.api_key", "");
    insertConfig("llm.api_key", "");
    insertConfig("llm.api_base_url", "");
    insertConfig("llm.protocol", "OPENAI_COMPATIBLE");
    insertConfig("llm.timeout_ms", "10000");
    insertConfig("llm.temperature", "0.2");
    insertConfig("llm.max_tokens", "1024");
    insertConfig("table.api_key", "");
    insertConfig("table.api_base_url", "");
    insertConfig("table.retry_max_count", "5");
    insertConfig("table.alert_notify_target", "ADMIN");
    insertConfig("skill.circuit_breaker_failure_rate", "0.5");
    insertConfig("image.compress_quality", "85");
    insertConfig("cache.sync_cron", "0 */30 * * * *");
    insertConfig("cache.ttl_seconds", "900");
    insertConfig("version.storage.root", "uploads/desktop-releases");
    insertConfig("version.storage.public_base_url", "/downloads/desktop-releases");
    insertConfig("desktop.clipboard_screenshot_confirm_prompt_s", "10");
    secretCipher = new SecretCipher("test-secret-key");
    service = new ConfigAdminService(
        jdbcTemplate,
        Mockito.mock(ApplicationEventPublisher.class),
        Mockito.mock(WsPushService.class),
        Mockito.mock(PromptVersionService.class),
        Mockito.mock(AuditLogger.class),
        new ObjectMapper(),
        secretCipher);
  }

  @Test
  void apiKeyConfigIsStoredEncryptedAndReturnedMasked() {
    service.update("skill.api_key", Map.of("value", "live-secret-1234"));

    String stored = jdbcTemplate.queryForObject(
        "SELECT config_value FROM system_configs WHERE config_key = ?",
        String.class,
        "skill.api_key");
    assertThat(stored).startsWith("{aes-gcm}");
    assertThat(stored).doesNotContain("live-secret-1234");
    assertThat(secretCipher.decrypt(stored)).isEqualTo("live-secret-1234");
    assertThat(service.get("skill.api_key").get("value")).isEqualTo("****1234");
    assertThat(service.list("skill.").get("skill.api_key")).isEqualTo("****1234");
  }

  @Test
  void tableApiKeyIsStoredEncryptedAndRuntimeValuesAreValidated() {
    service.update("table.api_key", Map.of("value", "table-secret-4321"));
    service.update("table.api_base_url", Map.of("value", "https://table.example.com"));
    service.update("table.retry_max_count", Map.of("value", "8"));
    service.update("table.alert_notify_target", Map.of("value", "BOTH"));
    service.update("skill.circuit_breaker_failure_rate", Map.of("value", "0.25"));
    service.update("image.compress_quality", Map.of("value", "90"));
    service.update("cache.ttl_seconds", Map.of("value", "1800"));

    String stored = jdbcTemplate.queryForObject(
        "SELECT config_value FROM system_configs WHERE config_key = ?",
        String.class,
        "table.api_key");
    assertThat(stored).startsWith("{aes-gcm}");
    assertThat(secretCipher.decrypt(stored)).isEqualTo("table-secret-4321");
    assertThat(service.get("table.api_key").get("value")).isEqualTo("****4321");

    assertThatThrownBy(() -> service.update("table.retry_max_count", Map.of("value", "2")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("table.retry_max_count range is 3-10");
    assertThatThrownBy(() -> service.update("table.alert_notify_target", Map.of("value", "KEEPER")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("ADMIN, LEADER or BOTH");
    assertThatThrownBy(() -> service.update("skill.circuit_breaker_failure_rate", Map.of("value", "1.5")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("skill.circuit_breaker_failure_rate range is 0.05-1");
    assertThatThrownBy(() -> service.update("image.compress_quality", Map.of("value", "50")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("image.compress_quality range is 60-95");
    assertThatThrownBy(() -> service.update("cache.sync_cron", Map.of("value", "")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cache.sync_cron must not be blank");
  }

  @Test
  void llmConfigIsEncryptedAndRuntimeValuesAreValidated() {
    service.update("llm.api_key", Map.of("value", "llm-secret-9999"));
    service.update("llm.api_base_url", Map.of("value", "https://llm.example.com"));
    service.update("llm.protocol", Map.of("value", "OPENAI_COMPATIBLE"));
    service.update("llm.timeout_ms", Map.of("value", "15000"));
    service.update("llm.temperature", Map.of("value", "0.7"));
    service.update("llm.max_tokens", Map.of("value", "2048"));

    String stored = jdbcTemplate.queryForObject(
        "SELECT config_value FROM system_configs WHERE config_key = ?",
        String.class,
        "llm.api_key");
    assertThat(stored).startsWith("{aes-gcm}");
    assertThat(secretCipher.decrypt(stored)).isEqualTo("llm-secret-9999");
    assertThat(service.get("llm.api_key").get("value")).isEqualTo("****9999");

    assertThatThrownBy(() -> service.update("llm.api_base_url", Map.of("value", "llm.example.com")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("config value must be valid URL");
    assertThatThrownBy(() -> service.update("llm.protocol", Map.of("value", "CUSTOM")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("llm.protocol must be OPENAI_COMPATIBLE");
    assertThatThrownBy(() -> service.update("llm.timeout_ms", Map.of("value", "999")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("llm.timeout_ms range is 1000-60000");
    assertThatThrownBy(() -> service.update("llm.temperature", Map.of("value", "2.1")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("llm.temperature range is 0-2");
    assertThatThrownBy(() -> service.update("llm.max_tokens", Map.of("value", "0")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("llm.max_tokens range is 1-32000");
  }

  @Test
  void versionStorageConfigValidatesPathAndPublicBaseUrl() {
    service.update("version.storage.root", Map.of("value", "D:/pda-releases"));
    service.update("version.storage.public_base_url", Map.of("value", "https://cdn.example.com/desktop-releases"));

    assertThat(service.get("version.storage.root").get("value")).isEqualTo("D:/pda-releases");
    assertThat(service.get("version.storage.public_base_url").get("value")).isEqualTo("https://cdn.example.com/desktop-releases");

    assertThatThrownBy(() -> service.update("version.storage.root", Map.of("value", "/")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("non-root storage path");
    assertThatThrownBy(() -> service.update("version.storage.public_base_url", Map.of("value", "cdn.example.com/releases")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("absolute http(s) URL or absolute path");
  }

  @Test
  void desktopRuntimeConfigValidatesClipboardScreenshotConfirmPromptSeconds() {
    service.update("desktop.clipboard_screenshot_confirm_prompt_s", Map.of("value", "0"));
    assertThat(service.get("desktop.clipboard_screenshot_confirm_prompt_s").get("value")).isEqualTo("0");

    service.update("desktop.clipboard_screenshot_confirm_prompt_s", Map.of("value", "3"));
    service.update("desktop.clipboard_screenshot_confirm_prompt_s", Map.of("value", "60"));
    assertThat(service.get("desktop.clipboard_screenshot_confirm_prompt_s").get("value")).isEqualTo("60");

    assertThatThrownBy(() -> service.update("desktop.clipboard_screenshot_confirm_prompt_s", Map.of("value", "-1")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("desktop.clipboard_screenshot_confirm_prompt_s range is 0 or 3-60");
    assertThatThrownBy(() -> service.update("desktop.clipboard_screenshot_confirm_prompt_s", Map.of("value", "2")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("desktop.clipboard_screenshot_confirm_prompt_s range is 0 or 3-60");
    assertThatThrownBy(() -> service.update("desktop.clipboard_screenshot_confirm_prompt_s", Map.of("value", "61")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("desktop.clipboard_screenshot_confirm_prompt_s range is 0 or 3-60");
    assertThatThrownBy(() -> service.update("desktop.clipboard_screenshot_confirm_prompt_s", Map.of("value", "abc")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("config value must be integer");
  }

  private void insertConfig(String key, String value) {
    jdbcTemplate.update("INSERT INTO system_configs (config_key, config_value) VALUES (?, ?)", key, value);
  }
}
