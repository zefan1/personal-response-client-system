package com.privateflow.modules.skill;

import java.util.List;

public record SkillResponse(
    List<Suggestion> suggestions,
    CustomerAnalysis customerAnalysis,
    FollowupSuggest followupSuggest,
    ProfileUpdates profileUpdates
) {
}
