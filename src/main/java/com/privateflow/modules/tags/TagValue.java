package com.privateflow.modules.tags;

import java.time.LocalDateTime;
import java.util.List;

public record TagValue(
    long id,
    long categoryId,
    String categoryKey,
    String tagValue,
    String displayName,
    String meaning,
    String applicableWhen,
    String notApplicableWhen,
    String positiveExamples,
    String negativeExamples,
    List<String> synonyms,
    boolean systemSelectable,
    boolean manualSelectable,
    boolean isEnabled,
    int sortOrder,
    Long mergedIntoId,
    int version,
    TagImpact impact,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

  public TagValue(
      long id,
      long categoryId,
      String categoryKey,
      String tagValue,
      String displayName,
      boolean isEnabled,
      int sortOrder,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this(
        id,
        categoryId,
        categoryKey,
        tagValue,
        displayName,
        "",
        "",
        "",
        "",
        "",
        List.of(),
        true,
        true,
        isEnabled,
        sortOrder,
        null,
        0,
        TagImpact.empty(),
        createdAt,
        updatedAt);
  }

  public TagValue withImpact(TagImpact nextImpact) {
    return new TagValue(
        id, categoryId, categoryKey, tagValue, displayName, meaning, applicableWhen,
        notApplicableWhen, positiveExamples, negativeExamples, synonyms,
        systemSelectable, manualSelectable, isEnabled, sortOrder, mergedIntoId,
        version, nextImpact == null ? TagImpact.empty() : nextImpact, createdAt, updatedAt);
  }
}
