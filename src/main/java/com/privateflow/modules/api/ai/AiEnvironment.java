package com.privateflow.modules.api.ai;

import java.time.LocalDateTime;

public record AiEnvironment(
    long id,
    String envName,
    String provider,
    String baseUrl,
    String apiKeyLast4,
    boolean active,
    LocalDateTime lastTestAt,
    Boolean lastTestOk,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
