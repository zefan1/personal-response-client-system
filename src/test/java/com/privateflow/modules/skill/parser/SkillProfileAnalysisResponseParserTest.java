package com.privateflow.modules.skill.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.skill.SkillErrorCodes;
import com.privateflow.modules.skill.SkillGatewayException;
import com.privateflow.modules.skill.TagAnalysisAction;
import com.privateflow.modules.skill.TagAnalysisResultType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SkillProfileAnalysisResponseParserTest {

  private final SkillProfileAnalysisResponseParser parser = new SkillProfileAnalysisResponseParser(new ObjectMapper());

  @Test
  void parsesProfileFieldsAndTypedTagDecisions() {
    var result = parser.parse("""
        {
          "profile_updates": {
            "fields": {
              "nickname": {"value": "Alice", "confidence": "HIGH"}
            },
            "tag_decisions": [
              {
                "category_code": "custom_goal",
                "tag_codes": ["GOAL_B"],
                "confidence": 0.95,
                "evidence": "客户明确说想改善核心力量",
                "result_type": "UPDATE",
                "requested_action": "ADD"
              },
              {
                "category_code": "intent_level",
                "tag_codes": [],
                "confidence": 0.30,
                "evidence": "当前信息不足",
                "result_type": "UNABLE_TO_DETERMINE",
                "requested_action": "NONE"
              }
            ]
          }
        }
        """);

    assertThat(result.profileUpdates().fields()).containsKey("nickname");
    assertThat(result.tagDecisions()).hasSize(2);
    assertThat(result.tagDecisions().get(0).resultType()).isEqualTo(TagAnalysisResultType.UPDATE);
    assertThat(result.tagDecisions().get(0).requestedAction()).isEqualTo(TagAnalysisAction.ADD);
    assertThat(result.tagDecisions().get(0).confidence()).isEqualByComparingTo(new BigDecimal("0.95"));
    assertThat(result.tagDecisions().get(1).resultType()).isEqualTo(TagAnalysisResultType.UNABLE_TO_DETERMINE);
  }

  @Test
  void rejectsLegacyOrMalformedProfileAnalysisSchema() {
    assertInvalid("""
        {"profile_updates":{"tag_decisions":[]}}
        """);
    assertInvalid("""
        {"profile_updates":{"fields":{"intentLevel":{"value":"HIGH","confidence":"HIGH"}}}}
        """);
    assertInvalid("""
        {"profile_updates":{"fields":{},"tag_decisions":[{"category_code":"intent_level","tag_codes":[],"confidence":0.2,"evidence":"不足","result_type":"GUESS","requested_action":"NONE"}]}}
        """);
    assertInvalid("not-json");
  }

  private void assertInvalid(String raw) {
    assertThatThrownBy(() -> parser.parse(raw))
        .isInstanceOf(SkillGatewayException.class)
        .extracting(ex -> ((SkillGatewayException) ex).getErrorCode())
        .isEqualTo(SkillErrorCodes.SKILL_RESPONSE_INVALID);
  }
}
