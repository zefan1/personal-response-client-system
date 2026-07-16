package com.privateflow.modules.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillRequestTest {

  @Test
  void legacyConstructorUsesEmptyReplyTags() {
    SkillRequest request = new SkillRequest(
        Scene.ACTIVE_REPLY,
        "TUAN_GOU",
        "18800001111",
        "hello",
        Map.of(),
        Map.of(),
        List.of(),
        List.of(),
        "keeper");

    assertThat(request.currentTags()).isEmpty();
  }

  @Test
  void canonicalConstructorCopiesReplyTags() {
    List<ReplyTagSnapshot> tags = new ArrayList<>();
    tags.add(tag("LOYALIST"));
    SkillRequest request = new SkillRequest(
        Scene.ACTIVE_REPLY,
        "TUAN_GOU",
        "18800001111",
        "hello",
        Map.of(),
        Map.of(),
        List.of(),
        List.of(),
        "keeper",
        tags);

    tags.clear();

    assertThat(request.currentTags())
        .extracting(ReplyTagSnapshot::tagValue)
        .containsExactly("LOYALIST");
  }

  private ReplyTagSnapshot tag(String value) {
    return new ReplyTagSnapshot(
        "personality_type",
        "性格类型",
        value,
        "忠诚型",
        "重视安全感和专业背书",
        "MANUAL",
        "客户多次询问案例和保障",
        true);
  }
}
