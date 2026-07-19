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
    int profileExtractTimeoutMs,
    int regenerateMaxCount,
    String protocol
) {

  public SkillConfig(
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
      int profileExtractTimeoutMs,
      int regenerateMaxCount
  ) {
    this(apiBaseUrl, apiKey, phoneTransferMode, phoneEncryptionKey, timeoutMs,
        circuitBreakerWindowS, circuitBreakerFailureRate, circuitBreakerMinCalls,
        circuitBreakerOpenS, fallbackReply, tuanSkillGroupId, xiansuoSkillGroupId,
        defaultSkillId, systemPromptTemplate, redLines, alertFailureRate,
        alertFailureDurationMinutes, profileExtractTimeoutMs, regenerateMaxCount,
        "OPENAI_COMPATIBLE");
  }
}
