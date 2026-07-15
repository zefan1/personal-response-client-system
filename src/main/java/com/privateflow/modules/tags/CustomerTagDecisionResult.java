package com.privateflow.modules.tags;

public record CustomerTagDecisionResult(
    long categoryId,
    String categoryKey,
    String action,
    boolean updated,
    String reason
) {
}
