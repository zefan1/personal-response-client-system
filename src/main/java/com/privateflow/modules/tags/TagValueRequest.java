package com.privateflow.modules.tags;

import java.util.List;

public record TagValueRequest(
    Long categoryId,
    String tagValue,
    String displayName,
    String meaning,
    String applicableWhen,
    String notApplicableWhen,
    String positiveExamples,
    String negativeExamples,
    List<String> synonyms,
    Boolean systemSelectable,
    Boolean manualSelectable,
    Boolean isEnabled,
    Integer sortOrder,
    Integer version
) {

  public TagValueRequest(Long categoryId, String tagValue, String displayName, Boolean isEnabled, Integer sortOrder) {
    this(
        categoryId,
        tagValue,
        displayName,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        isEnabled,
        sortOrder,
        null);
  }

  public TagValueRequest(
      Long categoryId,
      String tagValue,
      String displayName,
      String meaning,
      String applicableWhen,
      String notApplicableWhen,
      String positiveExamples,
      String negativeExamples,
      List<String> synonyms,
      Boolean systemSelectable,
      Boolean manualSelectable,
      Boolean isEnabled,
      Integer sortOrder) {
    this(categoryId, tagValue, displayName, meaning, applicableWhen,
        notApplicableWhen, positiveExamples, negativeExamples, synonyms,
        systemSelectable, manualSelectable, isEnabled, sortOrder, null);
  }
}
