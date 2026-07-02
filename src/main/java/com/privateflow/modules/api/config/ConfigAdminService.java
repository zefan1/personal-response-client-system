package com.privateflow.modules.api.config;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.ai.PromptVersionService;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  public ConfigAdminService(
      JdbcTemplate jdbcTemplate,
      ApplicationEventPublisher eventPublisher,
      WsPushService wsPushService,
      PromptVersionService promptVersionService,
      AuditLogger auditLogger,
      ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
    this.promptVersionService = promptVersionService;
    this.auditLogger = auditLogger;
    this.objectMapper = objectMapper;
  }

  public Map<String, String> list(String prefix) {
    String like = prefix == null || prefix.isBlank() ? "%" : prefix + "%";
    return jdbcTemplate.query("""
        SELECT config_key, config_value FROM system_configs
        WHERE config_key LIKE ?
        ORDER BY config_key
        """, (rs, rowNum) -> Map.entry(rs.getString("config_key"), rs.getString("config_value")), like)
        .stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public Map<String, Object> get(String key) {
    String value = jdbcTemplate.query("""
        SELECT config_value FROM system_configs WHERE config_key = ? LIMIT 1
        """, (rs, rowNum) -> rs.getString("config_value"), key).stream().findFirst()
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "unknown config key"));
    return Map.of("configKey", key, "value", value);
  }

  @Transactional
  public Map<String, Object> update(String key, Map<String, Object> body) {
    if (key == null || key.isBlank() || body == null || !body.containsKey("value")) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "config key and value are required");
    }
    String value = String.valueOf(body.get("value"));
    validate(key, value);
    int updated = jdbcTemplate.update("""
        UPDATE system_configs SET config_value = ?, updated_at = NOW()
        WHERE config_key = ?
        """, value, key);
    if (updated == 0) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "unknown config key");
    }
    promptVersionService.snapshotIfPrompt(key, value, AuthContext.username(), "update config");
    auditLogger.log("UPDATE_CONFIG", AuthContext.username(), "system_configs", key, "updated config " + key);
    eventPublisher.publishEvent(new ConfigChangedEvent(key));
    wsPushService.broadcastWs(WsMessage.unsaved("CONFIG_REFRESH", Map.of("configKey", key)));
    return Map.of("updated", true, "configKey", key);
  }

  private void validate(String key, String value) {
    if (key.startsWith("system.") || key.startsWith("cache.") || key.startsWith("skill.")
        || key.startsWith("image.") || key.startsWith("match.") || key.startsWith("profile.")
        || key.startsWith("followup.") || key.startsWith("table.") || key.startsWith("datasource.") || key.startsWith("quicksearch.")
        || key.startsWith("tag.") || key.startsWith("version.") || key.startsWith("notice.") || key.startsWith("audit.")) {
      if (key.endsWith("_s") || key.endsWith("_ms") || key.endsWith("_days") || key.endsWith("_hours")
          || key.endsWith("_minutes") || key.endsWith("_count") || key.endsWith("_size") || key.endsWith("_limit")
          || key.endsWith("_chars") || key.endsWith("_rows") || key.endsWith("_seconds") || "audit.list_page_size_default".equals(key)) {
        int parsed;
        try {
          parsed = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
          throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "config value must be integer");
        }
        validateIntegerRange(key, parsed);
      }
      if ("skill.system_prompt_red_lines".equals(key) || "match.tag_removal_rules".equals(key)) {
        validateJsonArray(key, value);
      }
      if (key.endsWith("api_base_url") && !value.isBlank() && !value.startsWith("http://") && !value.startsWith("https://")) {
        throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "config value must be valid URL");
      }
      return;
    }
    throw new ApiException(ApiErrorCodes.BAD_REQUEST, "unknown config key");
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
    if ("image.compress_quality".equals(key) && (value < 1 || value > 100)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "image.compress_quality range is 1-100");
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
}
