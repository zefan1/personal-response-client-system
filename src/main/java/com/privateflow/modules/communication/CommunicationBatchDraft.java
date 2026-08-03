package com.privateflow.modules.communication;

import java.time.LocalDateTime;
import java.util.List;

public record CommunicationBatchDraft(
    String username,
    String platformCode,
    String platformIdentifier,
    String recognizedNickname,
    String recognizedPhone,
    Long customerId,
    String rawText,
    LocalDateTime recognizedAt,
    List<CommunicationMessageDraft> messages) {
}
