package com.privateflow.modules.api.audit;

import java.time.LocalDate;

public record AuditExportRequest(
    String action,
    String operator,
    String targetType,
    String targetId,
    String keyword,
    LocalDate startDate,
    LocalDate endDate) {
}
