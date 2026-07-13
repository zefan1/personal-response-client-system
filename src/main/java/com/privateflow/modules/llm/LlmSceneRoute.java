package com.privateflow.modules.llm;

import java.time.LocalDateTime;

public record LlmSceneRoute(
    long id,
    LlmScene scene,
    String leadType,
    long environmentId,
    String environmentName,
    String model,
    String protocol,
    int priority,
    boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
