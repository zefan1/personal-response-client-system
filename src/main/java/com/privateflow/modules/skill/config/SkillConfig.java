package com.privateflow.modules.skill.config;

public record SkillConfig(
    String apiBaseUrl,
    String apiKey,
    String phoneTransferMode,
    String phoneEncryptionKey,
    int timeoutMs,
    int circuitBreakerWindowS,
    double circuitBreakerFailureRate,
    int circuitBreakerMinCalls,
    int circuitBreakerOpenS,
    String fallbackReply,
    String tuanSkillGroupId,
    String xiansuoSkillGroupId,
    String defaultSkillId,
    String systemPromptTemplate,
    String redLines,
    double alertFailureRate,
    int alertFailureDurationMinutes,
    int profileExtractTimeoutMs
) {
}
