package com.privateflow.modules.tags;

import java.time.LocalDateTime;

public record TagAnalysisRun(
    long id,
    String analysisKey,
    long customerId,
    String sourceType,
    String status,
    int effectiveMessageCount,
    int customerVersion,
    String caller,
    String skillId,
    String llmEnvironment,
    String llmModel,
    String promptVersion,
    String errorMessage,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    LocalDateTime createdAt
) {
}
