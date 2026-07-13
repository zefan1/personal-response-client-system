package com.privateflow.modules.api.health;

import com.privateflow.modules.api.alert.SystemAlertRepository;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.image.health.ImageServiceHealthMonitor;
import com.privateflow.modules.runtime.RuntimeModeService;
import com.privateflow.modules.skill.health.SkillHealthMonitor;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.PendingTableWriteRepository;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

  private final DataSource dataSource;
  private final StringRedisTemplate redisTemplate;
  private final SystemAlertRepository alertRepository;
  private final SystemConfigRepository configRepository;
  private final SkillHealthMonitor skillHealthMonitor;
  private final ImageServiceHealthMonitor imageHealthMonitor;
  private final PendingTableWriteRepository tableWriteRepository;
  private final TableConfigProvider tableConfigProvider;
  private final RuntimeModeService runtimeModeService;
  private final Map<String, String> lastStatuses = new ConcurrentHashMap<>();
  private final Map<String, Instant> statusSince = new ConcurrentHashMap<>();

  public HealthService(
      DataSource dataSource,
      StringRedisTemplate redisTemplate,
      SystemAlertRepository alertRepository,
      SystemConfigRepository configRepository,
      SkillHealthMonitor skillHealthMonitor,
      ImageServiceHealthMonitor imageHealthMonitor,
      PendingTableWriteRepository tableWriteRepository,
      TableConfigProvider tableConfigProvider,
      RuntimeModeService runtimeModeService) {
    this.dataSource = dataSource;
    this.redisTemplate = redisTemplate;
    this.alertRepository = alertRepository;
    this.configRepository = configRepository;
    this.skillHealthMonitor = skillHealthMonitor;
    this.imageHealthMonitor = imageHealthMonitor;
    this.tableWriteRepository = tableWriteRepository;
    this.tableConfigProvider = tableConfigProvider;
    this.runtimeModeService = runtimeModeService;
  }

  public Map<String, Object> health() {
    requireAdmin();
    Map<String, Object> components = new LinkedHashMap<>();
    components.put("skill", skill());
    components.put("imageRecognition", imageRecognition());
    components.put("wecomTable", wecomTable());
    components.put("redis", redis());
    components.put("db", db());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", overallStatus(components));
    result.put("timestamp", LocalDateTime.now());
    result.put("runtimeMode", runtimeModeService.currentMode());
    result.put("components", components);
    result.put("recentAlerts", alertRepository.recentAlerts(
        intConfig("health.alert_history_days", 7),
        intConfig("health.alert_history_max", 100)));
    result.put("refreshIntervalS", intConfig("health.refresh_interval_s", 30));
    return result;
  }

  private Map<String, Object> db() {
    try (Connection connection = dataSource.getConnection()) {
      return component("db", connection.isValid(3) ? "UP" : "DOWN", Map.of());
    } catch (Exception ex) {
      return component("db", "DOWN", Map.of("message", "mysql unavailable"));
    }
  }

  private Map<String, Object> redis() {
    try {
      redisTemplate.hasKey("__health__");
      return component("redis", "UP", Map.of());
    } catch (RuntimeException ex) {
      return component("redis", "DOWN", Map.of("message", "redis unavailable"));
    }
  }

  private Map<String, Object> skill() {
    String circuitState = skillHealthMonitor.circuitState();
    String status = switch (circuitState) {
      case "OPEN" -> "DOWN";
      case "HALF_OPEN" -> "DEGRADED";
      default -> "UP";
    };
    return component("skill", status, Map.of(
        "circuitState", circuitState,
        "successRate5min", skillHealthMonitor.successRate5Min(),
        "totalCalls5min", skillHealthMonitor.totalCalls5Min()));
  }

  private Map<String, Object> imageRecognition() {
    String status = imageHealthMonitor.status().name();
    return component("imageRecognition", status, Map.of(
        "consecutiveFailures", imageHealthMonitor.consecutiveFailures(),
        "lastError", imageHealthMonitor.lastErrorMsg() == null ? "" : imageHealthMonitor.lastErrorMsg()));
  }

  private Map<String, Object> wecomTable() {
    try {
      int pending = tableWriteRepository.countPending();
      int staleFailed = tableWriteRepository.countStaleFailed(tableConfigProvider.get().alertFailureHours());
      String status = staleFailed > 0 ? "DOWN" : (pending >= tableConfigProvider.get().queueWarnThreshold() ? "DEGRADED" : "UP");
      return component("wecomTable", status, Map.of("pendingCount", pending, "staleFailedCount", staleFailed));
    } catch (RuntimeException ex) {
      return component("wecomTable", "UNKNOWN", Map.of("message", "table write health unavailable"));
    }
  }

  private Map<String, Object> component(String key, String status, Map<String, Object> detail) {
    Instant now = Instant.now();
    String previousStatus = lastStatuses.put(key, status);
    if (previousStatus == null || !previousStatus.equals(status)) {
      statusSince.put(key, now);
    }
    Instant since = statusSince.computeIfAbsent(key, unused -> now);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", status);
    result.put("lastCheckedAt", LocalDateTime.now());
    result.put("duration", Duration.between(since, now).isZero() ? "PT0S" : Duration.between(since, now).toString());
    if (detail != null && !detail.isEmpty()) {
      result.put("detail", detail);
    }
    return result;
  }

  private String overallStatus(Map<String, Object> components) {
    boolean down = components.values().stream().anyMatch(value -> value instanceof Map<?, ?> map && "DOWN".equals(map.get("status")));
    if (down) {
      return "DOWN";
    }
    boolean degraded = components.values().stream().anyMatch(value -> value instanceof Map<?, ?> map
        && ("DEGRADED".equals(map.get("status")) || "UNKNOWN".equals(map.get("status"))));
    return degraded ? "DEGRADED" : "UP";
  }

  private void requireAdmin() {
    AuthUser user = AuthContext.current();
    if (user == null || user.role() != Role.ADMIN) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "permission denied");
    }
  }

  private int intConfig(String key, int fallback) {
    return configRepository.findValue(key).map(value -> {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException ex) {
        return fallback;
      }
    }).orElse(fallback);
  }
}
