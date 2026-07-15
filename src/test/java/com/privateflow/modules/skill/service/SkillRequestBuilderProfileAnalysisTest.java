package com.privateflow.modules.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.skill.ProfileAnalysisContext;
import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.config.SkillConfig;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import com.privateflow.modules.tags.TagCandidateBuilder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkillRequestBuilderProfileAnalysisTest {

  @Test
  void buildsSanitizedStructuredPayloadAndUsesProfileExtractRoute() throws Exception {
    SkillConfigProvider configProvider = mock(SkillConfigProvider.class);
    SkillRuntimeRouter runtimeRouter = mock(SkillRuntimeRouter.class);
    SkillConfig config = config();
    when(configProvider.get()).thenReturn(config);
    when(runtimeRouter.route(Scene.PROFILE_EXTRACT, "TUAN_GOU", config))
        .thenReturn(Optional.of("profile-skill"));
    SkillRequestBuilder builder = new SkillRequestBuilder(
        configProvider,
        mock(CustomerQueryService.class),
        mock(TagCandidateBuilder.class),
        new ObjectMapper(),
        runtimeRouter);
    ProfileAnalysisContext context = context();

    Map<String, Object> payload = builder.buildProfileExtract(new ProfileExtractRequest(
        "旧摘要不应覆盖结构化聊天",
        Map.of("phone", "18800001111", "leadType", "TUAN_GOU"),
        List.of("nickname"),
        "keeper-1",
        context));

    verify(runtimeRouter).route(Scene.PROFILE_EXTRACT, "TUAN_GOU", config);
    assertThat(payload)
        .containsEntry("scene", "PROFILE_EXTRACT")
        .containsEntry("skill_id", "profile-skill")
        .containsEntry("skill_group_id", "profile-skill")
        .containsEntry("profile_analysis_context", context)
        .containsEntry("customer", context.customerProfile());
    assertThat(payload.get("chat_context")).isEqualTo(context.recentMessages());
    assertThat(String.valueOf(payload.get("system_prompt")))
        .contains("profile_updates")
        .contains("tag_decisions")
        .contains("category_code")
        .contains("tag_codes")
        .contains("result_type")
        .contains("requested_action");
    String json = new ObjectMapper().writeValueAsString(payload);
    assertThat(json)
        .contains("自定义目标")
        .contains("动态标签含义")
        .contains("客户真实原话")
        .doesNotContain("18800001111")
        .doesNotContain("keeper-1");
  }

  @Test
  void usesDedicatedProfilePromptInsteadOfReplyGenerationPrompt() {
    SkillRequestBuilder builder = builder();

    Map<String, Object> payload = builder.buildProfileExtract(request(context()));

    assertThat(String.valueOf(payload.get("system_prompt")))
        .contains("客户档案分析")
        .contains("role=client")
        .doesNotContain("生成 3 条不同方向的回复建议");
  }

  @Test
  void usesOnlyCustomerMessagesAsProfileClientMessage() {
    SkillRequestBuilder builder = builder();
    ProfileAnalysisContext context = new ProfileAnalysisContext(
        7L,
        4,
        1,
        List.of(
            new ProfileAnalysisContext.ConversationMessage("client", "客户真实原话", "12:00"),
            new ProfileAnalysisContext.ConversationMessage("keeper", "员工回复内容", "12:01")),
        Map.of("leadType", "TUAN_GOU"),
        List.of(),
        List.of(),
        List.of());

    Map<String, Object> payload = builder.buildProfileExtract(request(context));

    assertThat(payload.get("client_message")).isEqualTo("客户真实原话");
    assertThat(payload.get("chat_context")).isEqualTo(context.recentMessages());
  }

  @Test
  void delegatesProfileInputAndContractToSharedPromptBuilder() {
    SkillConfigProvider configProvider = mock(SkillConfigProvider.class);
    SkillRuntimeRouter runtimeRouter = mock(SkillRuntimeRouter.class);
    ProfileAnalysisPromptBuilder promptBuilder = mock(ProfileAnalysisPromptBuilder.class);
    SkillConfig config = config();
    ProfileExtractRequest request = request(context());
    when(configProvider.get()).thenReturn(config);
    when(promptBuilder.build(request, "不得夸大效果"))
        .thenReturn(new ProfileAnalysisPromptBuilder.ProfileAnalysisPrompt(
            "shared strict contract",
            Map.of(
                "client_message", "shared client evidence",
                "profile_analysis_context", request.analysisContext())));
    SkillRequestBuilder builder = new SkillRequestBuilder(
        configProvider,
        mock(CustomerQueryService.class),
        mock(TagCandidateBuilder.class),
        new ObjectMapper(),
        runtimeRouter,
        promptBuilder);

    Map<String, Object> payload = builder.buildProfileExtract(request);

    verify(promptBuilder).build(request, "不得夸大效果");
    assertThat(payload)
        .containsEntry("system_prompt", "shared strict contract")
        .containsEntry("client_message", "shared client evidence")
        .containsEntry("profile_analysis_context", request.analysisContext());
  }

  private SkillRequestBuilder builder() {
    SkillConfigProvider configProvider = mock(SkillConfigProvider.class);
    SkillRuntimeRouter runtimeRouter = mock(SkillRuntimeRouter.class);
    SkillConfig config = config();
    when(configProvider.get()).thenReturn(config);
    when(runtimeRouter.route(Scene.PROFILE_EXTRACT, "TUAN_GOU", config))
        .thenReturn(Optional.of("profile-skill"));
    return new SkillRequestBuilder(
        configProvider,
        mock(CustomerQueryService.class),
        mock(TagCandidateBuilder.class),
        new ObjectMapper(),
        runtimeRouter);
  }

  private ProfileExtractRequest request(ProfileAnalysisContext context) {
    return new ProfileExtractRequest(
        "旧摘要不应覆盖结构化聊天",
        Map.of("leadType", "TUAN_GOU"),
        List.of("nickname"),
        "keeper-1",
        context);
  }

  private ProfileAnalysisContext context() {
    return new ProfileAnalysisContext(
        7L,
        4,
        1,
        List.of(new ProfileAnalysisContext.ConversationMessage("client", "客户真实原话", "12:00")),
        Map.of("nickname", "Alice", "phoneLast4", "1111", "leadType", "TUAN_GOU"),
        List.of(new ProfileAnalysisContext.CurrentTag(
            "custom_goal", "自定义目标", "GOAL_A", "目标 A", "MANUAL")),
        List.of(),
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

  private SkillConfig config() {
    return new SkillConfig(
        "http://localhost",
        "key",
        "LAST_FOUR",
        "",
        1000,
        30,
        0.5,
        5,
        30,
        "fallback",
        "legacy-tuan",
        "legacy-xiansuo",
        "legacy-default",
        "请生成 3 条不同方向的回复建议。\n{{available_tags}}\n{{red_lines}}\n{{scene}}",
        "不得夸大效果",
        0.3,
        15,
        8000,
        3);
  }
}
