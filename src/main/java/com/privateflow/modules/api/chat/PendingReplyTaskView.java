package com.privateflow.modules.api.chat;

import com.privateflow.modules.match.CustomerSummary;
import java.time.LocalDateTime;
import java.util.List;

public record PendingReplyTaskView(
    String taskId,
    String replySessionId,
    PendingReplyTaskStatus status,
    List<CustomerSummary> candidates,
    String selectedPhone,
    ChatResponse response,
    String errorCode,
    LocalDateTime expiresAt
) {
}
