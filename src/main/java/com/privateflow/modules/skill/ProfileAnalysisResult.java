package com.privateflow.modules.skill;

import java.util.List;

public record ProfileAnalysisResult(
    ProfileUpdates profileUpdates,
    List<TagAnalysisDecision> tagDecisions
) {
  public ProfileAnalysisResult {
    profileUpdates = profileUpdates == null ? ProfileUpdates.empty() : profileUpdates;
    tagDecisions = tagDecisions == null ? List.of() : List.copyOf(tagDecisions);
  }

  public static ProfileAnalysisResult empty() {
    return new ProfileAnalysisResult(ProfileUpdates.empty(), List.of());
  }
}
