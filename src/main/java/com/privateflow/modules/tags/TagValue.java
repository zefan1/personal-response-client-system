package com.privateflow.modules.tags;

import java.time.LocalDateTime;

public record TagValue(
    long id,
    long categoryId,
    String categoryKey,
    String tagValue,
    String displayName,
    boolean isEnabled,
    int sortOrder,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
