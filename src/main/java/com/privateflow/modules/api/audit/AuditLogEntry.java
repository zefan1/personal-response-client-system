package com.privateflow.modules.api.audit;

import java.time.LocalDateTime;

public record AuditLogEntry(
    long id,
    String operator,
    String action,
    String targetType,
    String targetId,
    String detail,
    LocalDateTime createdAt) {
}
