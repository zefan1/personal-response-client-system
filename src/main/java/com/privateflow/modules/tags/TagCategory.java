package com.privateflow.modules.tags;

import java.time.LocalDateTime;
import java.util.List;

public record TagCategory(
    long id,
    String categoryKey,
    String categoryName,
    String boundField,
    boolean isBuiltin,
    boolean isEnabled,
    int sortOrder,
    List<TagValue> values,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
