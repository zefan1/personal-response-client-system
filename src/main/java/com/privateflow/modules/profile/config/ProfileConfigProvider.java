package com.privateflow.modules.profile.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ProfileConfigProvider {

  private static final Logger log = LoggerFactory.getLogger(ProfileConfigProvider.class);
  private final SystemConfigRepository configRepository;
  private final ObjectMapper objectMapper;
  private final AtomicReference<ProfileConfig> current;

  public ProfileConfigProvider(
      SystemConfigRepository configRepository,
      ObjectMapper objectMapper,
      @Value("${profile.extract-fields:}") List<String> extractFields,
      @Value("${profile.extract-timeout-ms:8000}") int extractTimeoutMs,
      @Value("${profile.send-confirm-window-s:5}") int sendConfirmWindowS,
      @Value("${profile.suggestion-expire-days:7}") int suggestionExpireDays,
      @Value("${profile.suggestion-cleanup-cron:0 0 3 * * *}") String suggestionCleanupCron,
      @Value("${profile.suggestion-max-per-customer:20}") int suggestionMaxPerCustomer,
      @Value("${profile.dedup-window-s:5}") int dedupWindowS,
      @Value("${profile.fallback-summary-chars:500}") int fallbackSummaryChars) {
    this.configRepository = configRepository;
    this.objectMapper = objectMapper;
    List<String> fields = extractFields == null || extractFields.isEmpty() ? ProfileConfig.defaultExtractFields() : extractFields;
    this.current = new AtomicReference<>(new ProfileConfig(
        fields,
        clamp(extractTimeoutMs, 5000, 12000),
        clamp(sendConfirmWindowS, 3, 15),
        clamp(suggestionExpireDays, 3, 30),
        suggestionCleanupCron,
        clamp(suggestionMaxPerCustomer, 10, 50),
        clamp(dedupWindowS, 3, 15),
        clamp(fallbackSummaryChars, 200, 1000)));
  }

  @PostConstruct
  public void load() {
    refresh();
  }

  public ProfileConfig get() {
    return current.get();
  }

  @EventListener
  public void onConfigChanged(ConfigChangedEvent event) {
    if (event.configKey() != null && event.configKey().startsWith("profile.")) {
      refresh();
    }
  }

  public void refresh() {
    ProfileConfig previous = current.get();
    try {
      Map<String, String> values = configRepository.findByPrefix("profile.");
      current.set(new ProfileConfig(
          readFields(values.get("profile.extract_fields"), previous.extractFields()),
          readInt(values.get("profile.extract_timeout_ms"), previous.extractTimeoutMs(), 5000, 12000),
          readInt(values.get("profile.send_confirm_window_s"), previous.sendConfirmWindowS(), 3, 15),
          readInt(values.get("profile.suggestion_expire_days"), previous.suggestionExpireDays(), 3, 30),
          readString(values.get("profile.suggestion_cleanup_cron"), previous.suggestionCleanupCron()),
          readInt(values.get("profile.suggestion_max_per_customer"), previous.suggestionMaxPerCustomer(), 10, 50),
          readInt(values.get("profile.dedup_window_s"), previous.dedupWindowS(), 3, 15),
          readInt(values.get("profile.fallback_summary_chars"), previous.fallbackSummaryChars(), 200, 1000)));
    } catch (RuntimeException ex) {
      log.warn("failed to refresh profile config, keeping previous snapshot");
    }
  }

  private List<String> readFields(String raw, List<String> fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      List<String> parsed = objectMapper.readValue(raw, new TypeReference<>() {});
      List<String> filtered = parsed.stream().filter(value -> value != null && !value.isBlank()).toList();
      return filtered.isEmpty() ? fallback : filtered;
    } catch (Exception ex) {
      log.warn("invalid profile.extract_fields JSON, using previous value");
      return fallback;
    }
  }

  private String readString(String raw, String fallback) {
    return raw == null || raw.isBlank() ? fallback : raw;
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
}
