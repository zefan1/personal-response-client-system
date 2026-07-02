package com.privateflow.modules.skill;

public interface SkillGatewayService {
  SkillResponse generateReplies(SkillRequest request);

  ProfileUpdates extractProfile(ProfileExtractRequest request);
}
