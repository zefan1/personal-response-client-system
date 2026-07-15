package com.privateflow.modules.skill.admin;

import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.Suggestion;
import java.util.List;

public record SkillTestResponse(
    List<Suggestion> suggestions,
    long responseTimeMs,
    SkillResponse rawResponse,
    ProfileAnalysisResult profileAnalysis
) {
  public SkillTestResponse(List<Suggestion> suggestions, long responseTimeMs, SkillResponse rawResponse) {
    this(suggestions, responseTimeMs, rawResponse, null);
  }
}
