package com.privateflow.modules.api.chat;

/** Read-only task capacity values for the employee workbench. */
public record ChatTaskRuntimeConfigResponse(
    int unfinishedTaskCap,
    int recentTaskDisplayCap
) {
}
