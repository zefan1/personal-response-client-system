package com.privateflow.modules.api.health;

import com.privateflow.modules.api.alert.SystemAlertRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

  private final DataSource dataSource;
  private final StringRedisTemplate redisTemplate;
  private final SystemAlertRepository alertRepository;

  public HealthService(DataSource dataSource, StringRedisTemplate redisTemplate, SystemAlertRepository alertRepository) {
    this.dataSource = dataSource;
    this.redisTemplate = redisTemplate;
    this.alertRepository = alertRepository;
  }

  public Map<String, Object> health() {
    Map<String, Object> components = new LinkedHashMap<>();
    components.put("mysql", mysql());
    components.put("redis", redis());
    components.put("alerts", alertRepository.activeAlerts());
    boolean up = components.values().stream().noneMatch(value -> value instanceof Map<?, ?> map && "DOWN".equals(map.get("status")));
    return Map.of("status", up ? "UP" : "DOWN", "components", components);
  }

  private Map<String, Object> mysql() {
    try (var connection = dataSource.getConnection()) {
      return Map.of("status", connection.isValid(3) ? "UP" : "DOWN");
    } catch (Exception ex) {
      return Map.of("status", "DOWN", "message", "mysql unavailable");
    }
  }

  private Map<String, Object> redis() {
    try {
      redisTemplate.hasKey("__health__");
      return Map.of("status", "UP");
    } catch (RuntimeException ex) {
      return Map.of("status", "DOWN", "message", "redis unavailable");
    }
  }
}
