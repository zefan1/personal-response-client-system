package com.privateflow.modules.api.ai;

public record AiEnvironmentRequest(
    String envName,
    String baseUrl,
    String apiKey
) {
}
