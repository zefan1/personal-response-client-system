package com.privateflow.modules.skill;

import java.util.List;

public record SkillResponse(
    List<Suggestion> suggestions,
    CustomerAnalysis customerAnalysis,
    FollowupSuggest followupSuggest,
    ProfileUpdates profileUpdates,
    String guidance
) {
  public SkillResponse(
      List<Suggestion> suggestions,
      CustomerAnalysis customerAnalysis,
      FollowupSuggest followupSuggest,
      ProfileUpdates profileUpdates) {
    this(suggestions, customerAnalysis, followupSuggest, profileUpdates, null);
  }

  public static SkillResponse guidanceOnly(String guidance) {
    return new SkillResponse(List.of(), null, null, null, guidance);
  }
}
