package com.privateflow.modules.api.chat;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

@Component
public class ChatTaskConfig {

  private static final Settings DEFAULTS = new Settings(24, 120);
  private final SystemConfigRepository configRepository;
  private final AtomicReference<Settings> current = new AtomicReference<>(DEFAULTS);

  public ChatTaskConfig(SystemConfigRepository configRepository) {
    this.configRepository = configRepository;
  }

  @PostConstruct
  public void load() {
    refresh();
  }

  public int pendingReplyTtlHours() {
    return current.get().pendingReplyTtlHours();
  }

  public int pendingReplyGeneratingTimeoutSeconds() {
    return current.get().pendingReplyGeneratingTimeoutSeconds();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onConfigChanged(ConfigChangedEvent event) {
    if (event.configKey() != null && event.configKey().startsWith("chat.")) {
      refresh();
    }
  }

  public void refresh() {
    try {
      Map<String, String> values = configRepository.findByPrefix("chat.");
      Settings previous = current.get();
      current.set(new Settings(
          readInt(values.get("chat.pending_reply_ttl_hours"), previous.pendingReplyTtlHours(), 1, 72),
          readInt(
              values.get("chat.pending_reply_generating_timeout_s"),
              previous.pendingReplyGeneratingTimeoutSeconds(),
              30,
              600)));
    } catch (RuntimeException ignored) {
      // The defaults or last valid snapshot keep chat tasks usable when configuration is unavailable.
    }
  }

  private int readInt(String raw, int fallback, int min, int max) {
    try {
      return raw == null || raw.isBlank() ? fallback : clamp(Integer.parseInt(raw.trim()), min, max);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private record Settings(int pendingReplyTtlHours, int pendingReplyGeneratingTimeoutSeconds) {
  }
}
