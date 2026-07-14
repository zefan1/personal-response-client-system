package com.privateflow.modules.tags;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerTagAssignment(
    long id,
    long customerId,
    long categoryId,
    long tagValueId,
    TagSelectionMode selectionMode,
    boolean active,
    String sourceType,
    BigDecimal confidence,
    String evidenceText,
    int evidenceMessageCount,
    Long analysisResultId,
    String skillId,
    String llmEnvironment,
    String llmModel,
    String promptVersion,
    String operatorAccount,
    boolean manualLocked,
    String lockedBy,
    LocalDateTime lockedAt,
    Long supersedesAssignmentId,
    int customerVersion,
    String invalidatedReason,
    LocalDateTime invalidatedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long activeTagKey,
    Long activeSingleCategoryKey
) {
}
