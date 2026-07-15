package com.privateflow.modules.api.ai;

import com.privateflow.modules.llm.LlmScene;

public record LlmEnvironmentTestRequest(
    LlmScene scene,
    String leadType,
    String testMessage
) {
}
