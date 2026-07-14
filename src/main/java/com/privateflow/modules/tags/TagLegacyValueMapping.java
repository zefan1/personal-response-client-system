package com.privateflow.modules.tags;

import java.time.LocalDateTime;

public record TagLegacyValueMapping(
    long id,
    String sourceType,
    String legacyCategoryKey,
    String legacyValue,
    Long categoryId,
    Long tagValueId,
    String mappingStatus,
    String mappingNote,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
