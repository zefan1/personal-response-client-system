package com.privateflow.modules.tablewrite.config;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TableConfigProvider {

  private static final Set<String> ALERT_TARGETS = Set.of("ADMIN", "LEADER", "BOTH");
  private final SystemConfigRepository configRepository;
  private final AtomicReference<TableConfig> current;

  public TableConfigProvider(
      SystemConfigRepository configRepository,
      @Value("${table.write-timeout-ms:10000}") int writeTimeoutMs,
      @Value("${table.retry-max-count:5}") int retryMaxCount,
      @Value("${table.retry-interval-s:60}") int retryIntervalS,
      @Value("${table.alert-failure-hours:1}") int alertFailureHours,
      @Value("${table.alert-notify-target:ADMIN}") String alertNotifyTarget,
      @Value("${table.queue-warn-threshold:100}") int queueWarnThreshold,
      @Value("${table.queue-alert-threshold:1000}") int queueAlertThreshold) {
    this.configRepository = configRepository;
    this.current = new AtomicReference<>(new TableConfig(
        clamp(writeTimeoutMs, 5000, 20000),
        clamp(retryMaxCount, 3, 10),
        clamp(retryIntervalS, 30, 300),
        clamp(alertFailureHours, 1, 24),
        alertTarget(alertNotifyTarget, "ADMIN"),
        clamp(queueWarnThreshold, 50, 500),
        clamp(queueAlertThreshold, 500, 5000)));
  }

  @PostConstruct
  public void load() {
    refresh();
  }

  public TableConfig get() {
    return current.get();
  }

  @EventListener
  public void onConfigChanged(ConfigChangedEvent event) {
    if (event.configKey() != null && event.configKey().startsWith("table.")) {
      refresh();
    }
  }

  public void refresh() {
    TableConfig previous = current.get();
    Map<String, String> values = configRepository.findByPrefix("table.");
    current.set(new TableConfig(
        integer(values.get("table.write_timeout_ms"), previous.writeTimeoutMs(), 5000, 20000),
        integer(values.get("table.retry_max_count"), previous.retryMaxCount(), 3, 10),
        integer(values.get("table.retry_interval_s"), previous.retryIntervalS(), 30, 300),
        integer(values.get("table.alert_failure_hours"), previous.alertFailureHours(), 1, 24),
        alertTarget(values.get("table.alert_notify_target"), previous.alertNotifyTarget()),
        integer(values.get("table.queue_warn_threshold"), previous.queueWarnThreshold(), 50, 500),
        integer(values.get("table.queue_alert_threshold"), previous.queueAlertThreshold(), 500, 5000)));
  }

  private int integer(String raw, int fallback, int min, int max) {
    try {
      return raw == null || raw.isBlank() ? fallback : clamp(Integer.parseInt(raw.trim()), min, max);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static String alertTarget(String raw, String fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    String normalized = raw.trim().toUpperCase();
    return ALERT_TARGETS.contains(normalized) ? normalized : fallback;
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
