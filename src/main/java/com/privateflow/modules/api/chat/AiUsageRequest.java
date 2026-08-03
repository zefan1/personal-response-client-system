package com.privateflow.modules.api.chat;

public record AiUsageRequest(
    String phone,
    String taskId,
    String replySessionId,
    String replySource,
    String copiedText
) {
}
