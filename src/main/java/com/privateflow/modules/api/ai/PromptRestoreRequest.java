package com.privateflow.modules.api.ai;

public record PromptRestoreRequest(
    int version,
    String operator
) {
}
