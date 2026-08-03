package com.privateflow.modules.communication;

import java.time.LocalDateTime;

public record ArchivedCommunicationBatch(
    long id,
    String batchId,
    String username,
    String platformCode,
    String platformIdentifier,
    String recognizedNickname,
    String recognizedPhone,
    Long customerId,
    String rawText,
    LocalDateTime recognizedAt) {
}
