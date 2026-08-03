package com.privateflow.modules.communication;

import java.time.LocalDateTime;

public record CommunicationSummaryState(
    long customerId,
    String status,
    Long lastSummarizedMessageId,
    int retryCount,
    LocalDateTime nextRetryAt,
    String lastError,
    LocalDateTime updatedAt) {
}
