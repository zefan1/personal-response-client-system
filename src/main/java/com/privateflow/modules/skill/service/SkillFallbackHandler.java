package com.privateflow.modules.skill.service;

import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.Suggestion;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SkillFallbackHandler {

  private final SkillConfigProvider configProvider;

  public SkillFallbackHandler(SkillConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public SkillResponse fallback() {
    return new SkillResponse(
        List.of(new Suggestion(configProvider.get().fallbackReply(), "SYSTEM_FALLBACK", "")),
        null,
        null,
        null);
  }
}
