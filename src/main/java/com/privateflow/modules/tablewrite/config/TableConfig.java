package com.privateflow.modules.tablewrite.config;

public record TableConfig(
    String apiBaseUrl,
    String apiKey,
    int writeTimeoutMs,
    int retryMaxCount,
    int retryIntervalS,
    int alertFailureHours,
    String alertNotifyTarget,
    int queueWarnThreshold,
    int queueAlertThreshold
) {
}
