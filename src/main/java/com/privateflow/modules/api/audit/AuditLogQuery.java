package com.privateflow.modules.api.audit;

import java.time.LocalDate;
import java.util.List;

public record AuditLogQuery(
    List<String> actions,
    String operator,
    String targetType,
    String targetId,
    String keyword,
    LocalDate startDate,
    LocalDate endDate,
    int page,
    int size) {
}
