package com.privateflow.modules.tags;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TagCategory(
    long id,
    String categoryKey,
    String categoryName,
    String purpose,
    String boundField,
    TagSelectionMode selectionMode,
    boolean systemInferenceEnabled,
    boolean manualEditEnabled,
    TagAutoUpdateMode autoUpdateMode,
    BigDecimal minConfidence,
    int minEvidenceMessages,
    int cooldownHours,
    TagUncertainPolicy uncertainPolicy,
    boolean useForReply,
    boolean useForFilter,
    boolean useForStatistics,
    boolean useForFollowupRules,
    boolean isBuiltin,
    boolean isEnabled,
    int sortOrder,
    Long mergedIntoId,
    int version,
    List<TagValue> values,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

  public TagCategory(
      long id,
      String categoryKey,
      String categoryName,
      String boundField,
      boolean isBuiltin,
      boolean isEnabled,
      int sortOrder,
      List<TagValue> values,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this(
        id,
        categoryKey,
        categoryName,
        "",
        boundField,
        TagSelectionMode.SINGLE,
        false,
        true,
        TagAutoUpdateMode.RECORD_ONLY,
        new BigDecimal("0.8500"),
        1,
        0,
        TagUncertainPolicy.KEEP_CURRENT,
        true,
        true,
        true,
        true,
        isBuiltin,
        isEnabled,
        sortOrder,
        null,
        0,
        values,
        createdAt,
        updatedAt);
  }
}
