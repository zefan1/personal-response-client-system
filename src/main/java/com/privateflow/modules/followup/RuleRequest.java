package com.privateflow.modules.followup;

public record RuleRequest(
    String name,
    String conditionJson,
    ActionType actionType,
    String actionConfig,
    Integer priority,
    Boolean enabled
) {
}
