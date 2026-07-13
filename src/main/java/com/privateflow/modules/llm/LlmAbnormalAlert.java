package com.privateflow.modules.llm;

public record LlmAbnormalAlert(
    String alertType,
    String level,
    String message
) {
}
