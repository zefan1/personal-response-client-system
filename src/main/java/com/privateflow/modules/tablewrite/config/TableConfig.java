package com.privateflow.modules.tablewrite.config;

public record TableConfig(
    int writeTimeoutMs,
    int retryMaxCount,
    int retryIntervalS,
    int alertFailureHours,
    String alertNotifyTarget,
    int queueWarnThreshold,
    int queueAlertThreshold
) {
}
