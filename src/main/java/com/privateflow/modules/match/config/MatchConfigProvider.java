package com.privateflow.modules.match.config;

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
public class MatchConfigProvider {

  private static final Logger log = LoggerFactory.getLogger(MatchConfigProvider.class);
  private final SystemConfigRepository configRepository;
  private final ObjectMapper objectMapper;
  private final AtomicReference<MatchConfig> current;

  public MatchConfigProvider(
      SystemConfigRepository configRepository,
      ObjectMapper objectMapper,
      @Value("${match.tag-removal-rules:L1-,L2-,A-,VIP-,V-}") List<String> tagRemovalRules,
      @Value("${match.max-candidates:5}") int maxCandidates,
      @Value("${match.fuzzy-search-timeout-ms:2000}") int fuzzySearchTimeoutMs,
      @Value("${match.confidence-ratio-threshold:0.5}") double confidenceRatioThreshold,
      @Value("${match.confidence-min-length:2}") int confidenceMinLength) {
    this.configRepository = configRepository;
    this.objectMapper = objectMapper;
    this.current = new AtomicReference<>(new MatchConfig(
        tagRemovalRules,
        clamp(maxCandidates, 3, 10),
        clamp(fuzzySearchTimeoutMs, 1000, 5000),
        clamp(confidenceRatioThreshold, 0.3d, 0.8d),
        clamp(confidenceMinLength, 2, 4)));
  }

  @PostConstruct
  public void load() {
    refresh();
  }

  public MatchConfig get() {
    return current.get();
  }

  @EventListener
  public void onConfigChanged(ConfigChangedEvent event) {
    if (event.configKey() != null && event.configKey().startsWith("match.")) {
      refresh();
    }
  }

  public void refresh() {
    MatchConfig base = current.get();
    try {
      Map<String, String> values = configRepository.findByPrefix("match.");
      current.set(new MatchConfig(
          readRules(values.get("match.tag_removal_rules"), base.tagRemovalRules()),
          readInt(values.get("match.max_candidates"), base.maxCandidates(), 3, 10),
          readInt(values.get("match.fuzzy_search_timeout_ms"), base.fuzzySearchTimeoutMs(), 1000, 5000),
          readDouble(values.get("match.confidence_ratio_threshold"), base.confidenceRatioThreshold(), 0.3d, 0.8d),
          readInt(values.get("match.confidence_min_length"), base.confidenceMinLength(), 2, 4)));
    } catch (RuntimeException ex) {
      log.warn("failed to refresh match config, keeping previous snapshot");
    }
  }

  private List<String> readRules(String raw, List<String> fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      List<String> parsed = objectMapper.readValue(raw, new TypeReference<>() {});
      List<String> filtered = parsed.stream().filter(s -> s != null && !s.isBlank()).toList();
      return filtered.isEmpty() ? fallback : filtered;
    } catch (Exception ex) {
      log.warn("invalid match.tag_removal_rules JSON, using previous value");
      return fallback;
    }
  }

  private int readInt(String raw, int fallback, int min, int max) {
    try {
      return raw == null || raw.isBlank() ? fallback : clamp(Integer.parseInt(raw.trim()), min, max);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private double readDouble(String raw, double fallback, double min, double max) {
    try {
      return raw == null || raw.isBlank() ? fallback : clamp(Double.parseDouble(raw.trim()), min, max);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
