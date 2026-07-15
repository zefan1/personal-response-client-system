package com.privateflow.modules.skill.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.privateflow.modules.skill.ProfileAnalysisContext;
import com.privateflow.modules.skill.ProfileExtractRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfileAnalysisPromptBuilderTest {

  private final ProfileAnalysisPromptBuilder builder = new ProfileAnalysisPromptBuilder();

  @Test
  void buildsSharedStructuredInputAndStrictOutputContract() {
    ProfileAnalysisContext context = context();

    ProfileAnalysisPromptBuilder.ProfileAnalysisPrompt prompt = builder.build(
        new ProfileExtractRequest(
            "旧摘要不应覆盖结构化上下文",
            Map.of("phone", "18800001111", "leadType", "TUAN_GOU"),
            List.of("nickname"),
            "keeper-1",
            context),
        "不得夸大效果");

    assertThat(prompt.input())
        .containsEntry("profile_analysis_context", context)
        .containsEntry("target_fields", List.of("nickname"))
        .containsEntry("customer", context.customerProfile())
        .containsEntry("chat_context", context.recentMessages())
        .containsEntry("client_message", "客户真实原话");
    assertThat(prompt.systemPrompt())
        .contains("客户档案分析")
        .contains("不得夸大效果")
        .contains("profile_updates")
        .contains("tag_decisions")
        .contains("category_code")
        .contains("requested_action")
        .doesNotContain("生成 3 条不同方向的回复建议");
    assertThat(String.valueOf(prompt.input().get("client_message")))
        .doesNotContain("员工回复内容")
        .doesNotContain("18800001111")
        .doesNotContain("keeper-1");
  }

  private ProfileAnalysisContext context() {
    return new ProfileAnalysisContext(
        7L,
        4,
        1,
        List.of(
            new ProfileAnalysisContext.ConversationMessage("client", "客户真实原话", "12:00"),
            new ProfileAnalysisContext.ConversationMessage("keeper", "员工回复内容", "12:01")),
        Map.of("nickname", "Alice", "phoneLast4", "1111", "leadType", "TUAN_GOU"),
        List.of(new ProfileAnalysisContext.CurrentTag(
            "custom_goal", "自定义目标", "GOAL_A", "目标 A", "MANUAL")),
        List.of(new ProfileAnalysisContext.LockedCategory(
            "intent_level", "意向等级", "人工修改")),
        List.of(new ProfileAnalysisContext.CategoryCandidate(
            "custom_goal",
            "自定义目标",
            "动态分类用途",
            "MULTI",
            "ADD_ONLY",
            new BigDecimal("0.9100"),
            2,
            12,
            "KEEP_CURRENT",
            List.of(new ProfileAnalysisContext.TagCandidate(
                "GOAL_A",
                "目标 A",
                "动态标签含义",
                "明确表达目标时",
                "信息不足时",
                "我想改善核心力量",
                "我再看看",
                List.of("核心目标"))))));
  }
}
