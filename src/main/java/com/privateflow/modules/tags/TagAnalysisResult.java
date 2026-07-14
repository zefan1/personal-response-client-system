package com.privateflow.modules.tags;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TagAnalysisResult(
    long id,
    long analysisRunId,
    long categoryId,
    Long tagValueId,
    String resultType,
    String requestedAction,
    BigDecimal confidence,
    String evidenceText,
    String validationStatus,
    String validationReason,
    LocalDateTime createdAt
) {
}
