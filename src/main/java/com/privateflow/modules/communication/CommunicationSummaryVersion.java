package com.privateflow.modules.communication;

import java.time.LocalDateTime;

public record CommunicationSummaryVersion(
    long id,
    long customerId,
    int versionNo,
    String summaryText,
    long lastMessageId,
    LocalDateTime generatedAt) {
}
