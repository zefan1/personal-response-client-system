package com.privateflow.modules.skill.admin;

import com.privateflow.modules.skill.Scene;

public record SkillBindingRequest(
    String skillId,
    String skillName,
    Scene scene,
    String leadType,
    Integer priority
) {
}
