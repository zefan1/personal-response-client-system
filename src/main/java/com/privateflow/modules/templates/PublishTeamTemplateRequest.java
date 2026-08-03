package com.privateflow.modules.templates;

public record PublishTeamTemplateRequest(
    String title,
    String shortcutCode,
    String leadType,
    Boolean enabled
) {
}
