package com.privateflow.modules.llm;

import com.privateflow.modules.skill.ProfileAnalysisResult;

public record LlmProfileExtractionTestResult(
    boolean success,
    long elapsedMs,
    String model,
    String protocol,
    ProfileAnalysisResult profileAnalysis,
    String errorCode,
    String errorMessage
) {
}
