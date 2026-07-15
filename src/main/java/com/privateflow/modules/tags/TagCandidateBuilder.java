package com.privateflow.modules.tags;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TagCandidateBuilder {

  private final TagDirectoryService directoryService;

  public TagCandidateBuilder(TagDirectoryService directoryService) {
    this.directoryService = directoryService;
  }

  public List<TagCategory> build(TagCandidatePurpose purpose) {
    requirePurpose(purpose);
    return directoryService.getSnapshot().categories().stream()
        .filter(category -> isCategoryAllowed(purpose, category))
        .map(category -> category.withValues(category.values().stream()
            .filter(value -> isValueAllowed(purpose, value))
            .toList()))
        .filter(category -> !category.values().isEmpty())
        .toList();
  }

  boolean isAllowed(TagCandidatePurpose purpose, TagCategory category, TagValue value) {
    requirePurpose(purpose);
    return isCategoryAllowed(purpose, category) && isValueAllowed(purpose, value);
  }

  private void requirePurpose(TagCandidatePurpose purpose) {
    if (purpose == null) {
      throw new IllegalArgumentException("标签候选用途不能为空");
    }
  }

  boolean isCategoryAllowed(TagCandidatePurpose purpose, TagCategory category) {
    if (!category.isEnabled() || category.mergedIntoId() != null) {
      return false;
    }
    return switch (purpose) {
      case SYSTEM_INFERENCE -> category.systemInferenceEnabled();
      case MANUAL_ASSIGNMENT, IMPORT -> category.manualEditEnabled();
      case FOLLOWUP_RULE -> category.useForFollowupRules();
      case REPLY -> category.useForReply();
      case FILTER -> category.useForFilter();
      case STATISTICS -> category.useForStatistics();
    };
  }

  private boolean isValueAllowed(TagCandidatePurpose purpose, TagValue value) {
    if (!value.isEnabled() || value.mergedIntoId() != null) {
      return false;
    }
    return switch (purpose) {
      case SYSTEM_INFERENCE -> value.systemSelectable();
      case MANUAL_ASSIGNMENT, IMPORT -> value.manualSelectable();
      case FOLLOWUP_RULE, REPLY, FILTER, STATISTICS -> true;
    };
  }
}
