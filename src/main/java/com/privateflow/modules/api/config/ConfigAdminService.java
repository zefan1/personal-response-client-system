package com.privateflow.modules.api.config;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.ai.PromptVersionService;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.security.SecretCipher;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigAdminService {

  private final JdbcTemplate jdbcTemplate;
  private final ApplicationEventPublisher eventPublisher;
  private final WsPushService wsPushService;
  private final PromptVersionService promptVersionService;
  private final AuditLogger auditLogger;
  private final ObjectMapper objectMapper;
  private final SecretCipher secretCipher;

  public ConfigAdminService(
      JdbcTemplate jdbcTemplate,
      ApplicationEventPublisher eventPublisher,
      WsPushService wsPushService,
      PromptVersionService promptVersionService,
      AuditLogger auditLogger,
      ObjectMapper objectMapper,
      SecretCipher secretCipher) {
    this.jdbcTemplate = jdbcTemplate;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
    this.promptVersionService = promptVersionService;
    this.auditLogger = auditLogger;
    this.objectMapper = objectMapper;
    this.secretCipher = secretCipher;
  }

  public Map<String, String> list(String prefix) {
    String like = prefix == null || prefix.isBlank() ? "%" : prefix + "%";
    return jdbcTemplate.query("""
        SELECT config_key, config_value FROM system_configs
        WHERE config_key LIKE ?
        ORDER BY config_key
        """, (rs, rowNum) -> Map.entry(rs.getString("config_key"), publicValue(rs.getString("config_key"), rs.getString("config_value"))), like)
        .stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public Map<String, Object> get(String key) {
    String value = jdbcTemplate.query("""
        SELECT config_value FROM system_configs WHERE config_key = ? LIMIT 1
        """, (rs, rowNum) -> rs.getString("config_value"), key).stream().findFirst()
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "unknown config key"));
    return Map.of("configKey", key, "value", publicValue(key, value));
  }

  @Transactional
  public Map<String, Object> update(String key, Map<String, Object> body) {
    if (key == null || key.isBlank() || body == null || !body.containsKey("value")) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "config key and value are required");
    }
    String value = String.valueOf(body.get("value"));
    validate(key, value);
    String storedValue = storedValue(key, value);
    int updated = jdbcTemplate.update("""
        UPDATE system_configs SET config_value = ?, updated_at = NOW()
        WHERE config_key = ?
        """, storedValue, key);
    if (updated == 0) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "unknown config key");
    }
    promptVersionService.snapshotIfPrompt(key, storedValue, AuthContext.username(), "update config");
    auditLogger.log("UPDATE_CONFIG", AuthContext.username(), "system_configs", key, "updated config " + key);
    eventPublisher.publishEvent(new ConfigChangedEvent(key));
    wsPushService.broadcastWs(WsMessage.unsaved("CONFIG_REFRESH", Map.of("configKey", key)));
    return Map.of("updated", true, "configKey", key);
  }

  private void validate(String key, String value) {
    if (key.startsWith("system.") || key.startsWith("cache.") || key.startsWith("skill.") || key.startsWith("llm.")
        || key.startsWith("image.") || key.startsWith("match.") || key.startsWith("profile.")
        || key.startsWith("followup.") || key.startsWith("table.") || key.startsWith("datasource.") || key.startsWith("quicksearch.") || key.startsWith("health.")
        || key.startsWith("desktop.")
        || key.startsWith("tag.") || key.startsWith("version.") || key.startsWith("notice.") || key.startsWith("audit.")) {
      if (key.endsWith("_s") || key.endsWith("_ms") || key.endsWith("_days") || key.endsWith("_hours")
          || key.endsWith("_minutes") || key.endsWith("_count") || key.endsWith("_size") || key.endsWith("_limit")
          || key.endsWith("_chars") || key.endsWith("_rows") || key.endsWith("_seconds") || key.endsWith("_bytes")
          || key.endsWith("_px") || key.endsWith("_threshold") || key.endsWith("_quality") || key.endsWith("_tokens") || "audit.list_page_size_default".equals(key)
          || "skill.circuit_breaker_min_calls".equals(key) || "image.consecutive_failures_alert".equals(key)) {
        int parsed;
        try {
          parsed = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
          throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "config value must be integer");
        }
        validateIntegerRange(key, parsed);
      }
      if ("skill.circuit_breaker_failure_rate".equals(key) || "skill.alert_failure_rate".equals(key) || "llm.temperature".equals(key)) {
        double parsed;
        try {
          parsed = Double.parseDouble(value);
        } catch (NumberFormatException ex) {
          throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "config value must be decimal");
        }
        validateDecimalRange(key, parsed);
      }
      if ("skill.system_prompt_red_lines".equals(key) || "match.tag_removal_rules".equals(key)) {
        validateJsonArray(key, value);
      }
      if ("table.alert_notify_target".equals(key) && !("ADMIN".equals(value) || "LEADER".equals(value) || "BOTH".equals(value))) {
        throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "table.alert_notify_target must be ADMIN, LEADER or BOTH");
      }
      if ("cache.sync_cron".equals(key) && value.isBlank()) {
        throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "cache.sync_cron must not be blank");
      }
      if ("skill.subscription_expire_at".equals(key)) {
        validateOptionalIsoDateTime(key, value);
      }
      if ("llm.protocol".equals(key) && !"OPENAI_COMPATIBLE".equals(value)) {
        throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "llm.protocol must be OPENAI_COMPATIBLE");
      }
      if (key.endsWith("api_base_url") && !value.isBlank() && !value.startsWith("http://") && !value.startsWith("https://")) {
        throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "config value must be valid URL");
      }
      if (key.endsWith(".storage.root")) {
        validateStorageRoot(key, value);
      }
      if (key.endsWith(".storage.public_base_url")) {
        validatePublicBaseUrl(key, value);
      }
      return;
    }
    throw new ApiException(ApiErrorCodes.BAD_REQUEST, "unknown config key");
  }

  private String storedValue(String key, String value) {
    return isSecretKey(key) && !value.isBlank() ? secretCipher.encrypt(value) : value;
  }

  private String publicValue(String key, String value) {
    if (!isSecretKey(key) || value == null || value.isBlank()) {
      return value;
    }
    String decrypted = secretCipher.decrypt(value);
    if (decrypted.isBlank()) {
      return "";
    }
    String last4 = decrypted.length() <= 4 ? decrypted : decrypted.substring(decrypted.length() - 4);
    return "****" + last4;
  }

  private boolean isSecretKey(String key) {
    return key != null && key.endsWith(".api_key");
  }

  private void validateStorageRoot(String key, String value) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isBlank() || ".".equals(trimmed) || "/".equals(trimmed) || "\\".equals(trimmed)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, key + " must be a non-root storage path");
    }
  }

  private void validatePublicBaseUrl(String key, String value) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isBlank()) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, key + " must not be blank");
    }
    if (trimmed.startsWith("/")) {
      return;
    }
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      return;
    }
    throw new ApiException(ApiErrorCodes.CONFIG_INVALID, key + " must be an absolute http(s) URL or absolute path");
  }

  private void validateIntegerRange(String key, int value) {
    if ("skill.regenerate_max_count".equals(key) && (value < 0 || value > 10)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "skill.regenerate_max_count range is 0-10");
    }
    if ("skill.prompt_version_max".equals(key) && (value < 20 || value > 200)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "skill.prompt_version_max range is 20-200");
    }
    if ("skill.timeout_ms".equals(key) && (value < 5000 || value > 15000)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "skill.timeout_ms range is 5000-15000");
    }
    if ("skill.circuit_breaker_window_s".equals(key) && (value < 10 || value > 300)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "skill.circuit_breaker_window_s range is 10-300");
    }
    if ("skill.circuit_breaker_min_calls".equals(key) && (value < 1 || value > 100)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "skill.circuit_breaker_min_calls range is 1-100");
    }
    if ("skill.circuit_breaker_open_s".equals(key) && (value < 10 || value > 600)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "skill.circuit_breaker_open_s range is 10-600");
    }
    if ("skill.alert_failure_duration_minutes".equals(key) && (value < 1 || value > 120)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "skill.alert_failure_duration_minutes range is 1-120");
    }
    if ("profile.extract_timeout_ms".equals(key) && (value < 5000 || value > 12000)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "profile.extract_timeout_ms range is 5000-12000");
    }
    if ("image.timeout_ms".equals(key) && (value < 1000 || value > 60000)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "image.timeout_ms range is 1000-60000");
    }
    if ("image.max_size_bytes".equals(key) && (value < 1048576 || value > 20971520)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "image.max_size_bytes range is 1048576-20971520");
    }
    if ("image.max_dimension_px".equals(key) && (value < 640 || value > 4096)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "image.max_dimension_px range is 640-4096");
    }
    if ("image.compress_quality".equals(key) && (value < 60 || value > 95)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "image.compress_quality range is 60-95");
    }
    if ("image.consecutive_failures_alert".equals(key) && (value < 1 || value > 10)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "image.consecutive_failures_alert range is 1-10");
    }
    if ("llm.timeout_ms".equals(key) && (value < 1000 || value > 60000)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "llm.timeout_ms range is 1000-60000");
    }
    if ("llm.max_tokens".equals(key) && (value < 1 || value > 32000)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "llm.max_tokens range is 1-32000");
    }
    if ("table.write_timeout_ms".equals(key) && (value < 5000 || value > 20000)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "table.write_timeout_ms range is 5000-20000");
    }
    if ("table.retry_max_count".equals(key) && (value < 3 || value > 10)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "table.retry_max_count range is 3-10");
    }
    if ("table.retry_interval_s".equals(key) && (value < 30 || value > 300)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "table.retry_interval_s range is 30-300");
    }
    if ("table.alert_failure_hours".equals(key) && (value < 1 || value > 24)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "table.alert_failure_hours range is 1-24");
    }
    if ("table.queue_warn_threshold".equals(key) && (value < 50 || value > 500)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "table.queue_warn_threshold range is 50-500");
    }
    if ("table.queue_alert_threshold".equals(key) && (value < 500 || value > 5000)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "table.queue_alert_threshold range is 500-5000");
    }
    if ("cache.ttl_seconds".equals(key) && (value < 60 || value > 86400)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "cache.ttl_seconds range is 60-86400");
    }
    if ("cache.sync_timeout_ms".equals(key) && (value < 5000 || value > 60000)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "cache.sync_timeout_ms range is 5000-60000");
    }
    if ("datasource.mapping_version_max".equals(key) && (value < 20 || value > 200)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "datasource.mapping_version_max range is 20-200");
    }
    if ("datasource.import_max_rows".equals(key) && (value < 1000 || value > 10000)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "datasource.import_max_rows range is 1000-10000");
    }
    if ("datasource.manual_sync_timeout_s".equals(key) && (value < 30 || value > 120)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "datasource.manual_sync_timeout_s range is 30-120");
    }
    if ("datasource.sync_status_refresh_s".equals(key) && (value < 15 || value > 120)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "datasource.sync_status_refresh_s range is 15-120");
    }
    if ("quicksearch.admin.page_size".equals(key) && (value < 10 || value > 50)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "quicksearch.admin.page_size range is 10-50");
    }
    if ("quicksearch.admin.image_max_size_mb".equals(key) && (value < 1 || value > 50)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "quicksearch.admin.image_max_size_mb range is 1-50");
    }
    if ("quicksearch.admin.cos_retention_days".equals(key) && (value < 7 || value > 90)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "quicksearch.admin.cos_retention_days range is 7-90");
    }
    if ("system.jwt_access_token_ttl_s".equals(key) && (value < 300 || value > 86400)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "system.jwt_access_token_ttl_s range is 300-86400");
    }
    if ("system.jwt_refresh_token_ttl_s".equals(key) && (value < 3600 || value > 2592000)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "system.jwt_refresh_token_ttl_s range is 3600-2592000");
    }
    if ("system.audit_log_retention_days".equals(key) && (value < 30 || value > 365)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "system.audit_log_retention_days range is 30-365");
    }
    if ("system.audit_log_cleanup_batch_size".equals(key) && (value < 1000 || value > 10000)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "system.audit_log_cleanup_batch_size range is 1000-10000");
    }
    if ("system.login_fail_window_s".equals(key) && (value < 60 || value > 3600)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "system.login_fail_window_s range is 60-3600");
    }
    if ("tag.cache_refresh_interval_s".equals(key) && (value < 60 || value > 3600)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "tag.cache_refresh_interval_s range is 60-3600");
    }
    if ("tag.value_max_per_category".equals(key) && (value < 1 || value > 200)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "tag.value_max_per_category range is 1-200");
    }
    if ("version.max_file_size_mb".equals(key) && (value < 100 || value > 1000)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "version.max_file_size_mb range is 100-1000");
    }
    if ("version.cos_upload_timeout_s".equals(key) && (value < 60 || value > 600)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "version.cos_upload_timeout_s range is 60-600");
    }
    if ("version.report_interval_hours".equals(key) && (value < 6 || value > 72)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "version.report_interval_hours range is 6-72");
    }
    if ("notice.max_title_chars".equals(key) && (value < 50 || value > 200)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "notice.max_title_chars range is 50-200");
    }
    if ("notice.max_content_chars".equals(key) && (value < 100 || value > 2000)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "notice.max_content_chars range is 100-2000");
    }
    if ("notice.default_expire_days".equals(key) && (value < 1 || value > 30)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "notice.default_expire_days range is 1-30");
    }
    if ("notice.max_schedule_days".equals(key) && (value < 7 || value > 90)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "notice.max_schedule_days range is 7-90");
    }
    if ("notice.scan_interval_s".equals(key) && (value < 15 || value > 120)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "notice.scan_interval_s range is 15-120");
    }
    if ("notice.auto_expire_hours".equals(key) && (value < 1 || value > 24)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "notice.auto_expire_hours range is 1-24");
    }
    if ("notice.list_page_size".equals(key) && (value < 10 || value > 50)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "notice.list_page_size range is 10-50");
    }
    if ("audit.export_max_rows".equals(key) && (value < 1000 || value > 50000)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "audit.export_max_rows range is 1000-50000");
    }
    if ("audit.export_cos_retention_hours".equals(key) && (value < 24 || value > 720)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "audit.export_cos_retention_hours range is 24-720");
    }
    if ("audit.export_timeout_seconds".equals(key) && (value < 60 || value > 600)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "audit.export_timeout_seconds range is 60-600");
    }
    if ("audit.list_page_size_default".equals(key) && (value < 10 || value > 100)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "audit.list_page_size_default range is 10-100");
    }
    if ("audit.list_max_page_size".equals(key) && (value < 50 || value > 500)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "audit.list_max_page_size range is 50-500");
    }
    if ("health.refresh_interval_s".equals(key) && value != 0 && (value < 15 || value > 120)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "health.refresh_interval_s range is 0 or 15-120");
    }
    if ("health.alert_history_days".equals(key) && (value < 1 || value > 30)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "health.alert_history_days range is 1-30");
    }
    if ("health.alert_history_max".equals(key) && (value < 50 || value > 200)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "health.alert_history_max range is 50-200");
    }
    if ("desktop.clipboard_screenshot_confirm_prompt_s".equals(key) && value != 0 && (value < 3 || value > 60)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "desktop.clipboard_screenshot_confirm_prompt_s range is 0 or 3-60");
    }
  }

  private void validateDecimalRange(String key, double value) {
    if ("skill.circuit_breaker_failure_rate".equals(key) && (value < 0.05 || value > 1)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "skill.circuit_breaker_failure_rate range is 0.05-1");
    }
    if ("skill.alert_failure_rate".equals(key) && (value < 0.01 || value > 1)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "skill.alert_failure_rate range is 0.01-1");
    }
    if ("llm.temperature".equals(key) && (value < 0 || value > 2)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "llm.temperature range is 0-2");
    }
  }

  private void validateJsonArray(String key, String value) {
    try {
      if (!objectMapper.readTree(value).isArray()) {
        throw new ApiException(ApiErrorCodes.CONFIG_INVALID, key + " must be JSON array");
      }
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, key + " must be JSON array");
    }
  }

  private void validateOptionalIsoDateTime(String key, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    try {
      LocalDate.parse(value);
      return;
    } catch (DateTimeParseException ignored) {
      // Try date-time formats below.
    }
    try {
      OffsetDateTime.parse(value);
      return;
    } catch (DateTimeParseException ignored) {
      // Try local date-time without timezone below.
    }
    try {
      LocalDateTime.parse(value);
    } catch (DateTimeParseException ex) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, key + " must be ISO date or date-time");
    }
  }
}
