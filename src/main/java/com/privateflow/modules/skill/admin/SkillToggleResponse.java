package com.privateflow.modules.skill.admin;

public record SkillToggleResponse(
    long id,
    boolean enabled,
    String warning
) {
}
