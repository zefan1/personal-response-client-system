package com.privateflow.modules.llm;

public record LlmConfig(
    String apiBaseUrl,
    String apiKey,
    String model,
    String protocol,
    int timeoutMs,
    double temperature,
    int maxTokens
) {
}
