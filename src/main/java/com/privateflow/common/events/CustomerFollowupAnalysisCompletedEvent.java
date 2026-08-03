package com.privateflow.common.events;

import java.util.Map;

public record CustomerFollowupAnalysisCompletedEvent(
    String phone,
    Map<String, Object> fields
) {
}
