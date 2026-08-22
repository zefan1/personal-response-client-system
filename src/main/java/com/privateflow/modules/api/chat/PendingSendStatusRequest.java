package com.privateflow.modules.api.chat;

public record PendingSendStatusRequest(
    String confirmationId,
    String status,
    Integer reminderCount) {
}
