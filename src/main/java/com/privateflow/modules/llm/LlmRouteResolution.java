package com.privateflow.modules.llm;

record LlmRouteResolution(
    LlmScene scene,
    String leadType,
    Long routeId,
    Long environmentId,
    String environmentName,
    LlmConfig config,
    boolean fallbackToActive
) {
}
