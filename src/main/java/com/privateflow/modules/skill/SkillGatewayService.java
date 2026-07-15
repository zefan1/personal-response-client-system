package com.privateflow.modules.skill;

public interface SkillGatewayService {
  SkillResponse generateReplies(SkillRequest request);

  ProfileAnalysisResult extractProfile(ProfileExtractRequest request);
}
