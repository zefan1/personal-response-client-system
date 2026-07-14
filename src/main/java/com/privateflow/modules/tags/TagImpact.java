package com.privateflow.modules.tags;

public record TagImpact(
    long customerCount,
    long ruleCount,
    long historyCount,
    long activeAssignmentCount,
    long analysisCount,
    long suggestionCount,
    long legacyMappingCount,
    long unmatchedCount,
    long lockCount
) {

  public static TagImpact empty() {
    return new TagImpact(0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  public TagImpact withRuleCount(long count) {
    return new TagImpact(
        customerCount,
        count,
        historyCount,
        activeAssignmentCount,
        analysisCount,
        suggestionCount,
        legacyMappingCount,
        unmatchedCount,
        lockCount);
  }

  public boolean hasReferences() {
    return customerCount > 0 || ruleCount > 0 || historyCount > 0;
  }
}
