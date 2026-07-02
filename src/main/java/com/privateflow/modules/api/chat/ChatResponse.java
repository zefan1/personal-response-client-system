package com.privateflow.modules.api.chat;

import com.privateflow.modules.match.MatchResult;
import com.privateflow.modules.skill.SkillResponse;

public record ChatResponse(
    String phone,
    String nickname,
    boolean needsCustomerIdentifier,
    MatchResult match,
    SkillResponse skill,
    String warning
) {
}
