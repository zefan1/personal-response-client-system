package com.privateflow.modules.followup.service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record FollowupTagContext(Map<Long, Set<Long>> valueIdsByCategory) {

  public FollowupTagContext {
    valueIdsByCategory = valueIdsByCategory == null ? Map.of() : valueIdsByCategory.entrySet().stream()
        .collect(Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            entry -> entry.getValue() == null ? Set.of() : Set.copyOf(entry.getValue())));
  }

  public static FollowupTagContext empty() {
    return new FollowupTagContext(Map.of());
  }

  public static FollowupTagContext of(Map<Long, Set<Long>> values) {
    return new FollowupTagContext(values);
  }

  public boolean containsAny(long categoryId, Collection<Long> values) {
    Set<Long> current = valueIdsByCategory.getOrDefault(categoryId, Set.of());
    return values != null && values.stream().anyMatch(current::contains);
  }

  public boolean containsAll(long categoryId, Collection<Long> values) {
    return values != null && valueIdsByCategory.getOrDefault(categoryId, Set.of()).containsAll(values);
  }
}
