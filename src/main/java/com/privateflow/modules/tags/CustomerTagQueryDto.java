package com.privateflow.modules.tags;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerTagQueryDto(
    long assignmentId,
    long customerId,
    int customerVersion,
    long categoryId,
    String categoryKey,
    String categoryName,
    TagSelectionMode categorySelectionMode,
    boolean categoryEnabled,
    Long categoryMergedIntoId,
    int categoryVersion,
    long tagValueId,
    String tagValue,
    String tagDisplayName,
    boolean tagValueEnabled,
    Long tagValueMergedIntoId,
    int tagValueVersion,
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
    String invalidatedReason,
    LocalDateTime invalidatedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
