package com.privateflow.modules.tags;

import java.time.LocalDateTime;

public record UnmatchedLegacyTagValue(
    long id,
    long customerId,
    String sourceType,
    Long sourceRecordId,
    String legacyField,
    String rawValue,
    String rawValueHash,
    Long categoryId,
    Long mappedTagValueId,
    String status,
    String resolutionNote,
    String resolvedBy,
    LocalDateTime resolvedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
