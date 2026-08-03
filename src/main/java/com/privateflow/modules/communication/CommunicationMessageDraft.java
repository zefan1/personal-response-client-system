package com.privateflow.modules.communication;

import java.time.LocalDateTime;

public record CommunicationMessageDraft(
    String senderRole,
    String text,
    String contentType,
    LocalDateTime messageTime,
    boolean timeEstimated) {
}
