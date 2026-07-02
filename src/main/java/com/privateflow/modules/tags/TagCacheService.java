package com.privateflow.modules.tags;

import com.privateflow.common.events.ConfigChangedEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TagCacheService {

  private final TagRepository repository;
  private final AtomicReference<Map<String, List<TagValue>>> cache = new AtomicReference<>(Map.of());

  public TagCacheService(TagRepository repository) {
    this.repository = repository;
  }

  public Map<String, List<TagValue>> getAllEnabledTags() {
    Map<String, List<TagValue>> current = cache.get();
    if (current.isEmpty()) {
      refresh();
      current = cache.get();
    }
    return current;
  }

  public List<TagValue> getTagsByCategory(String categoryKey) {
    return getAllEnabledTags().getOrDefault(categoryKey, List.of());
  }

  public void refresh() {
    Map<String, List<TagValue>> grouped = new LinkedHashMap<>();
    for (TagValue value : repository.findEnabledForPrompt()) {
      grouped.computeIfAbsent(value.categoryKey(), ignored -> new ArrayList<>()).add(value);
    }
    cache.set(Map.copyOf(grouped));
  }

  @EventListener
  public void onConfigChanged(ConfigChangedEvent event) {
    if ("tag_config".equals(event.configKey())) {
      refresh();
    }
  }

  @Scheduled(fixedDelayString = "${tag.cache-refresh-interval-ms:300000}")
  public void scheduledRefresh() {
    refresh();
  }
}
