package com.privateflow.modules.desktop;

public record DesktopSkillStatusResponse(
    DesktopSkillStatus status,
    String expireAt,
    Integer daysLeft,
    String label
) {
}
