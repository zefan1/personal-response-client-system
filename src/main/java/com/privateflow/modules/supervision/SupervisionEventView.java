package com.privateflow.modules.supervision;

import java.time.LocalDateTime;

public record SupervisionEventView(
    long id,
    SupervisionEventType eventType,
    String operatorUsername,
    String customerPhoneMasked,
    String channelCode,
    String leadSource,
    String assignedKeeper,
    String scene,
    String replySource,
    String replyPreview,
    LocalDateTime occurredAt) {
}
