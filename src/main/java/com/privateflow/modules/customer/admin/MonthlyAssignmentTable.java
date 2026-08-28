package com.privateflow.modules.customer.admin;

import java.time.LocalDateTime;

public record MonthlyAssignmentTable(
    long id,
    String tableName,
    String monthKey,
    String documentId,
    String sheetId,
    String viewId,
    String uniqueFieldTitle,
    String documentUrl,
    String status,
    String errorMessage,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime activatedAt) {
}
