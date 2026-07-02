package com.privateflow.modules.api.audit;

import java.time.LocalDateTime;

public record AuditExportRecord(
    long id,
    String exportId,
    AuditExportStatus status,
    String filtersJson,
    long totalCount,
    String csvContent,
    String downloadUrl,
    String message,
    LocalDateTime expireAt,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime completedAt) {
}
