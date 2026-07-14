package com.privateflow.modules.tags;

import java.time.LocalDateTime;

public record CustomerTagCategoryLock(
    long id,
    long customerId,
    long categoryId,
    boolean locked,
    String lockedBy,
    String lockReason,
    LocalDateTime lockedAt,
    String unlockedBy,
    LocalDateTime unlockedAt,
    int version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
