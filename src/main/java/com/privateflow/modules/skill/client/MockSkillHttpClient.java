package com.privateflow.modules.skill.client;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mock-externals", havingValue = "true")
public class MockSkillHttpClient implements SkillHttpClient {

  @Override
  public String call(Map<String, Object> payload, int timeoutMs) {
    if ("PROFILE_EXTRACT".equals(payload.get("scene"))) {
      return """
          {
            "profile_updates": {
              "fields": {
                "body_concerns": {"value": "腹直肌分离", "confidence": "HIGH"},
                "intent_level": {"value": "MEDIUM", "confidence": "MEDIUM"}
              },
              "tag_decisions": []
            }
          }
          """;
    }
    return """
        {
          "suggestions": [
            {"text": "您好，可以先了解一下您的产后恢复情况，我再帮您判断适合的方案。", "direction": "推荐回复", "reason": "先收集需求"},
            {"text": "腹直肌修复我们会先做评估，不会直接承诺效果，您可以先来店里看一下。", "direction": "更稳妥", "reason": "符合企业红线"},
            {"text": "如果您方便，我可以帮您约一个评估时间，到店后老师会详细看情况。", "direction": "更推进", "reason": "推进预约"}
          ],
          "customer_analysis": {"intent": "了解修复方案", "emotion": "犹豫", "personality_type_suggest": "LOYALIST", "confidence": "HIGH"},
          "followup_suggest": {"next_contact_at": "2026-06-27", "next_contact_direction": "询问是否方便到店评估"},
          "profile_updates": {"fields": {}}
        }
        """;
  }
}
