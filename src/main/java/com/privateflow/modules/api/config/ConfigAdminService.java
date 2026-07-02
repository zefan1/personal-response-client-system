package com.privateflow.modules.api.config;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConfigAdminService {

  private final JdbcTemplate jdbcTemplate;
  private final ApplicationEventPublisher eventPublisher;
  private final WsPushService wsPushService;

  public ConfigAdminService(JdbcTemplate jdbcTemplate, ApplicationEventPublisher eventPublisher, WsPushService wsPushService) {
    this.jdbcTemplate = jdbcTemplate;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
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
    eventPublisher.publishEvent(new ConfigChangedEvent(key));
    wsPushService.broadcastWs(WsMessage.unsaved("CONFIG_REFRESH", Map.of("configKey", key)));
    return Map.of("updated", true, "configKey", key);
  }

  private void validate(String key, String value) {
    if (key.startsWith("system.") || key.startsWith("cache.") || key.startsWith("skill.")
        || key.startsWith("image.") || key.startsWith("match.") || key.startsWith("profile.")
        || key.startsWith("followup.") || key.startsWith("table.")) {
      if (key.endsWith("_s") || key.endsWith("_ms") || key.endsWith("_days") || key.endsWith("_hours")
          || key.endsWith("_minutes") || key.endsWith("_count") || key.endsWith("_size") || key.endsWith("_limit")) {
        try {
          Integer.parseInt(value);
        } catch (NumberFormatException ex) {
          throw new ApiException(ApiErrorCodes.BAD_REQUEST, "config value must be integer");
        }
      }
      return;
    }
    throw new ApiException(ApiErrorCodes.BAD_REQUEST, "unknown config key");
  }
}
