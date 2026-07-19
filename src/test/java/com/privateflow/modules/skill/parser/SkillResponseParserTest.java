package com.privateflow.modules.skill.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.skill.SkillResponse;
import org.junit.jupiter.api.Test;

class SkillResponseParserTest {

  private final SkillResponseParser parser = new SkillResponseParser(new ObjectMapper());

  @Test
  void parsesMcpExecutionResultAsSkillGuidance() {
    SkillResponse response = parser.parseReplies("""
        {
          "tool": "sales_champion_coach__query",
          "skill_name": "sales-champion-coach",
          "result": "先确认客户预算顾虑，再追问真实预算和优先目标。",
          "status": "executed",
          "service_degraded": false
        }
        """);

    assertThat(response.suggestions()).isEmpty();
    assertThat(response.guidance()).isEqualTo("先确认客户预算顾虑，再追问真实预算和优先目标。");
  }
}
