package com.privateflow.modules.api.ai;

public record AiEnvironmentRequest(
    String envName,
    String baseUrl,
    String apiKey,
    String model,
    String protocol,
    Integer timeoutMs,
    Double temperature,
    Integer maxTokens
) {
  public AiEnvironmentRequest(String envName, String baseUrl, String apiKey) {
    this(envName, baseUrl, apiKey, null, null, null, null, null);
  }
}
