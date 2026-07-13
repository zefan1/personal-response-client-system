package com.privateflow.modules.llm;

public record LlmRouteRequest(
    LlmScene scene,
    String leadType,
    Long environmentId,
    Integer priority,
    Boolean enabled
) {
}
