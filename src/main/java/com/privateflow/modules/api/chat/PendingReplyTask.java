package com.privateflow.modules.api.chat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record PendingReplyTask(
    long id,
    String taskId,
    String replySessionId,
    String username,
    PendingReplyTaskStatus status,
    String recognizedNickname,
    String recognizedPhone,
    String platformIdentifier,
    String leadType,
    String sourceTable,
    String clientMessage,
    List<Map<String, String>> chatContext,
    List<String> candidatePhones,
    String selectedPhone,
    String resultJson,
    String errorCode,
    LocalDateTime generationStartedAt,
    LocalDateTime expiresAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
