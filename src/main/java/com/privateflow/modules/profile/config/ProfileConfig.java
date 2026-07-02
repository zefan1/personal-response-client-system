package com.privateflow.modules.profile.config;

import java.util.List;

public record ProfileConfig(
    List<String> extractFields,
    int extractTimeoutMs,
    int sendConfirmWindowS,
    int suggestionExpireDays,
    String suggestionCleanupCron,
    int suggestionMaxPerCustomer,
    int dedupWindowS,
    int fallbackSummaryChars
) {

  public static List<String> defaultExtractFields() {
    return List.of(
        "postpartumMonths", "parity", "deliveryMethod", "breastfeeding",
        "lochiaPeriod", "bodyConcerns", "diastasisRecti", "urineLeakage",
        "pubicLumbago", "prevRepairExp", "postpartumCheck", "exerciseHabits",
        "worries", "intentLevel", "personalityType", "nextFollowupAt",
        "nextFollowupDir", "followupNotes");
  }
}
