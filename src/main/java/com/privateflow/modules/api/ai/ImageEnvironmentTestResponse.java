package com.privateflow.modules.api.ai;

import java.util.Map;

public record ImageEnvironmentTestResponse(
    boolean success,
    long elapsedMs,
    Map<String, Object> result,
    String errorCode,
    String errorMessage,
    String suggestion
) {
}
