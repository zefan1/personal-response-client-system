package com.privateflow.modules.customer.admin;

import java.time.LocalDateTime;

public record Datasource(
    long id,
    String name,
    String sheetId,
    String sourceTable,
    String description,
    boolean enabled,
    int mappingCount,
    LocalDateTime lastSyncAt,
    String syncStatus,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
