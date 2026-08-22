package com.privateflow.modules.api.chat;

public record PendingSendRequest(
    String confirmationId,
    Long customerId,
    String phone,
    String nickname,
    String copiedText,
    String replySource) {
}
