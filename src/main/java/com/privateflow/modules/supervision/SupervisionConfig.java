package com.privateflow.modules.supervision;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SupervisionConfig {

  private static final Settings DEFAULTS = new Settings(180, 30, 3, 20, 30, 4, 1440);
  private static final Set<String> MANAGED_CHAT_KEYS = Set.of(
      "chat.expired_reply_task_retention_days",
      "chat.unfinished_task_cap",
      "chat.recent_task_display_cap",
      "chat.recognition_concurrency");

  private final SystemConfigRepository configRepository;
  private final AtomicReference<Settings> current = new AtomicReference<>(DEFAULTS);

  public SupervisionConfig(SystemConfigRepository configRepository) {
    this.configRepository = configRepository;
  }

  @PostConstruct
  public void load() {
    refresh();
  }

  public int recordRetentionDays() {
    return current.get().recordRetentionDays();
  }

  public int technicalLogRetentionDays() {
    return current.get().technicalLogRetentionDays();
  }

  public int expiredReplyTaskRetentionDays() {
    return current.get().expiredReplyTaskRetentionDays();
  }

  public int unfinishedTaskCap() {
    return current.get().unfinishedTaskCap();
  }

  public int recentTaskDisplayCap() {
    return current.get().recentTaskDisplayCap();
  }

  public int recognitionConcurrency() {
    return current.get().recognitionConcurrency();
  }

  public int processingSlaMinutes() {
    return current.get().processingSlaMinutes();
  }

  @EventListener
  public void onConfigChanged(ConfigChangedEvent event) {
    if (event != null && shouldRefresh(event.configKey())) {
      refresh();
    }
  }

  public void refresh() {
    try {
      Map<String, String> supervisionValues = configRepository.findByPrefix("supervision.");
      Map<String, String> chatValues = configRepository.findByPrefix("chat.");
      current.set(new Settings(
          readOrDefault(supervisionValues, "supervision.record_retention_days", 180, 30, 730),
          readOrDefault(supervisionValues, "supervision.technical_log_retention_days", 30, 7, 180),
          readOrDefault(chatValues, "chat.expired_reply_task_retention_days", 3, 1, 14),
          readOrDefault(chatValues, "chat.unfinished_task_cap", 20, 10, 50),
          readOrDefault(chatValues, "chat.recent_task_display_cap", 30, 20, 100),
          readOrDefault(chatValues, "chat.recognition_concurrency", 4, 1, 16),
          readOrDefault(supervisionValues, "supervision.processing_sla_minutes", 1440, 15, 10080)));
    } catch (RuntimeException ignored) {
      // Keep the last complete, valid snapshot when configuration is unavailable or invalid.
    }
  }

  private boolean shouldRefresh(String key) {
    return key != null && (key.startsWith("supervision.") || MANAGED_CHAT_KEYS.contains(key));
  }

  private int readOrDefault(Map<String, String> values, String key, int defaultValue, int min, int max) {
    String raw = values.get(key);
    if (raw == null) {
      return defaultValue;
    }
    if (raw.isBlank()) {
      throw new IllegalArgumentException("invalid supervision configuration");
    }
    int value = Integer.parseInt(raw.trim());
    if (value < min || value > max) {
      throw new IllegalArgumentException("invalid supervision configuration");
    }
    return value;
  }

  private record Settings(
      int recordRetentionDays,
      int technicalLogRetentionDays,
      int expiredReplyTaskRetentionDays,
      int unfinishedTaskCap,
      int recentTaskDisplayCap,
      int recognitionConcurrency,
      int processingSlaMinutes) {
  }
}
