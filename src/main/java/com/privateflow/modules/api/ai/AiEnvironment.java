package com.privateflow.modules.api.ai;

import java.time.LocalDateTime;

public record AiEnvironment(
    long id,
    String envName,
    String provider,
    String baseUrl,
    String apiKeyLast4,
    String model,
    String protocol,
    Integer timeoutMs,
    Double temperature,
    Integer maxTokens,
    boolean active,
    LocalDateTime lastTestAt,
    Boolean lastTestOk,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
  public AiEnvironment(
      long id,
      String envName,
      String provider,
      String baseUrl,
      String apiKeyLast4,
      boolean active,
      LocalDateTime lastTestAt,
      Boolean lastTestOk,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this(id, envName, provider, baseUrl, apiKeyLast4, null, null, null, null, null, active, lastTestAt, lastTestOk, createdAt, updatedAt);
  }
}
