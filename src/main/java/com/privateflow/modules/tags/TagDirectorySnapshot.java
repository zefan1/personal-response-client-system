package com.privateflow.modules.tags;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TagDirectorySnapshot {

  private final List<TagCategory> categories;
  private final Map<Long, TagCategory> categoriesById;
  private final Map<String, TagCategory> categoriesByKey;
  private final Map<Long, TagValue> valuesById;
  private final Map<TagValueCode, TagValue> valuesByCategoryAndCode;
  private final Map<String, List<TagValue>> valuesByCode;
  private final Instant refreshedAt;

  private TagDirectorySnapshot(List<TagCategory> source, Instant refreshedAt) {
    this.refreshedAt = Objects.requireNonNull(refreshedAt, "refreshedAt");
    this.categories = source.stream().map(TagDirectorySnapshot::copyCategory).toList();

    Map<Long, TagCategory> nextCategoriesById = new LinkedHashMap<>();
    Map<String, TagCategory> nextCategoriesByKey = new LinkedHashMap<>();
    Map<Long, TagValue> nextValuesById = new LinkedHashMap<>();
    Map<TagValueCode, TagValue> nextValuesByCategoryAndCode = new LinkedHashMap<>();
    Map<String, List<TagValue>> nextValuesByCode = new LinkedHashMap<>();
    for (TagCategory category : categories) {
      nextCategoriesById.put(category.id(), category);
      nextCategoriesByKey.put(category.categoryKey(), category);
      for (TagValue value : category.values()) {
        nextValuesById.put(value.id(), value);
        nextValuesByCategoryAndCode.put(
            new TagValueCode(category.categoryKey(), value.tagValue()),
            value);
        nextValuesByCode.computeIfAbsent(value.tagValue(), ignored -> new ArrayList<>()).add(value);
      }
    }
    this.categoriesById = immutableMap(nextCategoriesById);
    this.categoriesByKey = immutableMap(nextCategoriesByKey);
    this.valuesById = immutableMap(nextValuesById);
    this.valuesByCategoryAndCode = immutableMap(nextValuesByCategoryAndCode);
    this.valuesByCode = immutableListMap(nextValuesByCode);
  }

  public static TagDirectorySnapshot from(List<TagCategory> categories, Instant refreshedAt) {
    return new TagDirectorySnapshot(List.copyOf(categories), refreshedAt);
  }

  public static TagDirectorySnapshot empty(Instant refreshedAt) {
    return new TagDirectorySnapshot(List.of(), refreshedAt);
  }

  public List<TagCategory> categories() {
    return categories;
  }

  public Map<Long, TagCategory> categoriesById() {
    return categoriesById;
  }

  public Map<String, TagCategory> categoriesByKey() {
    return categoriesByKey;
  }

  public Map<Long, TagValue> valuesById() {
    return valuesById;
  }

  public Map<TagValueCode, TagValue> valuesByCategoryAndCode() {
    return valuesByCategoryAndCode;
  }

  public Map<String, List<TagValue>> valuesByCode() {
    return valuesByCode;
  }

  public Instant refreshedAt() {
    return refreshedAt;
  }

  private static TagCategory copyCategory(TagCategory category) {
    List<TagValue> values = category.values() == null
        ? List.of()
        : category.values().stream().map(TagDirectorySnapshot::copyValue).toList();
    return category.withValues(values);
  }

  private static TagValue copyValue(TagValue value) {
    return new TagValue(
        value.id(),
        value.categoryId(),
        value.categoryKey(),
        value.tagValue(),
        value.displayName(),
        value.meaning(),
        value.applicableWhen(),
        value.notApplicableWhen(),
        value.positiveExamples(),
        value.negativeExamples(),
        value.synonyms() == null ? List.of() : List.copyOf(value.synonyms()),
        value.systemSelectable(),
        value.manualSelectable(),
        value.isEnabled(),
        value.sortOrder(),
        value.mergedIntoId(),
        value.version(),
        value.impact(),
        value.createdAt(),
        value.updatedAt());
  }

  private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }

  private static <K, V> Map<K, List<V>> immutableListMap(Map<K, List<V>> source) {
    Map<K, List<V>> copy = new LinkedHashMap<>();
    source.forEach((key, values) -> copy.put(key, List.copyOf(values)));
    return Collections.unmodifiableMap(copy);
  }
}
