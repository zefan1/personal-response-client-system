package com.privateflow.modules.followup.config;

public record FollowupConfig(
    String fullScanCron,
    String lightweightScanCron,
    int ruleRefreshIntervalS,
    int tuanAlertHours,
    int xiansuoAlertHours,
    int pendingAlertHours,
    int sleepRiskDays,
    int lossRiskDays,
    int appointmentRemindHours,
    int scanBatchSize,
    int scanTimeoutS,
    int reminderDedupDays,
    int tagSuggestionDedupDays,
    int cursorTtlS,
    int keeperOverdueLeaderHours
) {
}
