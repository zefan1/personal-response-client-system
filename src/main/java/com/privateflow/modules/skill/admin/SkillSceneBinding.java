package com.privateflow.modules.skill.admin;

import com.privateflow.modules.skill.Scene;
import java.time.LocalDateTime;

public record SkillSceneBinding(
    long id,
    String skillId,
    String skillName,
    Scene scene,
    String leadType,
    int priority,
    boolean enabled,
    LocalDateTime lastTestedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
