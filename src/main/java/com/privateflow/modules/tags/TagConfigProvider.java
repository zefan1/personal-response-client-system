package com.privateflow.modules.tags;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TagConfigProvider {

  private static final Logger log = LoggerFactory.getLogger(TagConfigProvider.class);
  private final SystemConfigRepository repository;
  private final AtomicReference<TagRuntimeConfig> current;

  public TagConfigProvider(
      SystemConfigRepository repository,
      @Value("${tag.cache-refresh-interval-s:300}") int cacheRefreshIntervalSeconds,
      @Value("${tag.value-max-per-category:50}") int valueMaxPerCategory) {
    this.repository = repository;
    this.current = new AtomicReference<>(new TagRuntimeConfig(
        clamp(cacheRefreshIntervalSeconds, 60, 3600),
        clamp(valueMaxPerCategory, 1, 200)));
  }

  @PostConstruct
  public void load() {
    refresh();
  }

  public TagRuntimeConfig get() {
    return current.get();
  }

  @EventListener
  public void onConfigChanged(ConfigChangedEvent event) {
    if (event.configKey() != null && event.configKey().startsWith("tag.")) {
      refresh();
    }
  }

  public void refresh() {
    TagRuntimeConfig previous = current.get();
    try {
      Map<String, String> values = repository.findByPrefix("tag.");
      current.set(new TagRuntimeConfig(
          integer(values.get("tag.cache_refresh_interval_s"), previous.cacheRefreshIntervalSeconds(), 60, 3600),
          integer(values.get("tag.value_max_per_category"), previous.valueMaxPerCategory(), 1, 200)));
    } catch (RuntimeException ex) {
      log.warn("failed to refresh tag config, keeping previous snapshot");
    }
  }

  private int integer(String raw, int fallback, int min, int max) {
    try {
      return raw == null || raw.isBlank() ? fallback : clamp(Integer.parseInt(raw.trim()), min, max);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
