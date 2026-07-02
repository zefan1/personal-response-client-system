package com.privateflow.modules.followup;

public record RuleSearchCriteria(
    int page,
    int size,
    String keyword,
    ActionType actionType,
    Boolean enabled
) {
}
