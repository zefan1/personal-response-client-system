package com.privateflow.modules.profile.infra;

import com.privateflow.common.events.CustomerMessageSentEvent;
import java.time.LocalDateTime;
import java.util.List;

public record ProfileUpdateFailureRecord(
    long id,
    long customerId,
    String phone,
    List<CustomerMessageSentEvent.ChatMessage> rawMessages,
    String operator,
    String stage,
    String errorCode,
    String errorMessage,
    String status,
    int retryCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
}
