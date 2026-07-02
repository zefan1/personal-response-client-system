package com.privateflow.modules.api.chat;

import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.SkillResponse;

public record RequestContext(SkillRequest request, SkillResponse response, int regenerateCount) {
}
