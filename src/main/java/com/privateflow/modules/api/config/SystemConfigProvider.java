package com.privateflow.modules.api.config;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SystemConfigProvider {

  private final SystemConfigRepository configRepository;
  private final AtomicReference<SystemConfig> current;

  public SystemConfigProvider(
      SystemConfigRepository configRepository,
      @Value("${system.jwt-secret:change-me-in-production-private-domain-assistant}") String jwtSecret,
      @Value("${system.jwt-expire-hours:24}") int jwtExpireHours,
      @Value("${system.jwt-refresh-days:7}") int jwtRefreshDays,
      @Value("${system.ws-heartbeat-s:30}") int wsHeartbeatS,
      @Value("${system.ws-timeout-s:60}") int wsTimeoutS,
      @Value("${system.ws-replay-queue-size:100}") int wsReplayQueueSize,
      @Value("${system.request-total-timeout-ms:15000}") int requestTotalTimeoutMs,
      @Value("${system.audit-log-retention-days:90}") int auditLogRetentionDays,
      @Value("${system.login-fail-limit:10}") int loginFailLimit,
      @Value("${system.login-fail-window-s:300}") int loginFailWindowS,
      @Value("${system.captcha-enabled:false}") boolean captchaEnabled,
      @Value("${system.captcha-provider:}") String captchaProvider,
      @Value("${system.captcha-app-id:}") String captchaAppId,
      @Value("${system.captcha-secret:}") String captchaSecret,
      @Value("${system.request-context-ttl-s:300}") int requestContextTtlS,
      @Value("${system.ws-offline-retention-days:7}") int wsOfflineRetentionDays,
      @Value("${system.alert-retention-days:30}") int alertRetentionDays,
      @Value("${system.config-change-channel:config:change}") String configChangeChannel,
      @Value("${system.ws-push-channel:ws:push}") String wsPushChannel) {
    this.configRepository = configRepository;
    this.current = new AtomicReference<>(new SystemConfig(
        string(jwtSecret, "change-me-in-production-private-domain-assistant"),
        clamp(jwtExpireHours, 1, 168),
        clamp(jwtRefreshDays, 1, 30),
        clamp(wsHeartbeatS, 15, 60),
        clamp(wsTimeoutS, 30, 120),
        clamp(wsReplayQueueSize, 50, 500),
        clamp(requestTotalTimeoutMs, 10000, 20000),
        clamp(auditLogRetentionDays, 30, 365),
        clamp(loginFailLimit, 3, 20),
        clamp(loginFailWindowS, 60, 3600),
        captchaEnabled,
        string(captchaProvider, ""),
        string(captchaAppId, ""),
        string(captchaSecret, ""),
        clamp(requestContextTtlS, 60, 600),
        clamp(wsOfflineRetentionDays, 1, 30),
        clamp(alertRetentionDays, 7, 90),
        string(configChangeChannel, "config:change"),
        string(wsPushChannel, "ws:push")));
  }

  @PostConstruct
  public void load() {
    refresh();
  }

  public SystemConfig get() {
    return current.get();
  }

  @EventListener
  public void onConfigChanged(ConfigChangedEvent event) {
    if (event.configKey() != null && event.configKey().startsWith("system.")) {
      refresh();
    }
  }

  public void refresh() {
    SystemConfig previous = current.get();
    Map<String, String> values = configRepository.findByPrefix("system.");
    current.set(new SystemConfig(
        string(values.get("system.jwt_secret"), previous.jwtSecret()),
        integer(
            secondsToHours(values.get("system.jwt_access_token_ttl_s")) == null
                ? values.get("system.jwt_expire_hours")
                : secondsToHours(values.get("system.jwt_access_token_ttl_s")),
            previous.jwtExpireHours(),
            1,
            168),
        integer(
            secondsToDays(values.get("system.jwt_refresh_token_ttl_s")) == null
                ? values.get("system.jwt_refresh_days")
                : secondsToDays(values.get("system.jwt_refresh_token_ttl_s")),
            previous.jwtRefreshDays(),
            1,
            30),
        integer(values.get("system.ws_heartbeat_s"), previous.wsHeartbeatS(), 15, 60),
        integer(values.get("system.ws_timeout_s"), previous.wsTimeoutS(), 30, 120),
        integer(values.get("system.ws_replay_queue_size"), previous.wsReplayQueueSize(), 50, 500),
        integer(values.get("system.request_total_timeout_ms"), previous.requestTotalTimeoutMs(), 10000, 20000),
        integer(values.get("system.audit_log_retention_days"), previous.auditLogRetentionDays(), 30, 365),
        integer(values.get("system.login_fail_limit"), previous.loginFailLimit(), 3, 20),
        integer(
            values.get("system.login_fail_window_s") == null
                ? legacyMinutesToSeconds(values.get("system.login_lock_minutes"))
                : values.get("system.login_fail_window_s"),
            previous.loginFailWindowS(),
            60,
            3600),
        bool(values.get("system.captcha_enabled"), previous.captchaEnabled()),
        string(values.get("system.captcha_provider"), previous.captchaProvider()),
        string(values.get("system.captcha_app_id"), previous.captchaAppId()),
        string(values.get("system.captcha_secret"), previous.captchaSecret()),
        integer(values.get("system.request_context_ttl_s"), previous.requestContextTtlS(), 60, 600),
        integer(values.get("system.ws_offline_retention_days"), previous.wsOfflineRetentionDays(), 1, 30),
        integer(values.get("system.alert_retention_days"), previous.alertRetentionDays(), 7, 90),
        string(values.get("system.config_change_channel"), previous.configChangeChannel()),
        string(values.get("system.ws_push_channel"), previous.wsPushChannel())));
  }

  private int integer(String raw, int fallback, int min, int max) {
    try {
      return raw == null || raw.isBlank() ? fallback : clamp(Integer.parseInt(raw.trim()), min, max);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static String string(String raw, String fallback) {
    return raw == null || raw.isBlank() ? fallback : raw.trim();
  }

  private static boolean bool(String raw, boolean fallback) {
    return raw == null || raw.isBlank() ? fallback : Boolean.parseBoolean(raw.trim());
  }

  private String legacyMinutesToSeconds(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return String.valueOf(Integer.parseInt(raw.trim()) * 60);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private String secondsToHours(String raw) {
    return secondsToUnit(raw, 3600);
  }

  private String secondsToDays(String raw) {
    return secondsToUnit(raw, 86400);
  }

  private String secondsToUnit(String raw, int unit) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return String.valueOf(Math.max(1, Integer.parseInt(raw.trim()) / unit));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
