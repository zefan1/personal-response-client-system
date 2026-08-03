package com.privateflow.modules.communication;

import java.time.LocalDateTime;

public record ArchivedCommunicationMessage(
    long id,
    long batchId,
    Long customerId,
    String username,
    String platformCode,
    String senderRole,
    String contentType,
    String originalText,
    String currentText,
    LocalDateTime messageTime,
    boolean timeEstimated,
    int sequenceNo,
    String dedupeFingerprint) {
}
