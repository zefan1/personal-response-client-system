package com.privateflow.modules.tags;

import java.math.BigDecimal;

public record TagCategoryRequest(
    String categoryName,
    String purpose,
    String boundField,
    TagSelectionMode selectionMode,
    Boolean systemInferenceEnabled,
    Boolean manualEditEnabled,
    TagAutoUpdateMode autoUpdateMode,
    BigDecimal minConfidence,
    Integer minEvidenceMessages,
    Integer cooldownHours,
    TagUncertainPolicy uncertainPolicy,
    Boolean useForReply,
    Boolean useForFilter,
    Boolean useForStatistics,
    Boolean useForFollowupRules,
    Boolean isEnabled,
    Integer sortOrder
) {

  public TagCategoryRequest(String categoryName, String boundField, Boolean isEnabled, Integer sortOrder) {
    this(
        categoryName,
        null,
        boundField,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        isEnabled,
        sortOrder);
  }
}
