package com.privateflow.modules.followup;

import java.time.LocalDateTime;

public record FollowupRule(
    Long id,
    String name,
    String conditionJson,
    ActionType actionType,
    String actionConfig,
    int priority,
    boolean enabled,
    boolean builtin,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
