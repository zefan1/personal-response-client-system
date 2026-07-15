package com.privateflow.modules.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.profile.service.TagAnalysisDecisionValidator;
import com.privateflow.modules.skill.ProfileAnalysisContext;
import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.parser.SkillProfileAnalysisResponseParser;
import com.privateflow.modules.skill.service.ProfileAnalysisPromptBuilder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class LlmProfileExtractionServiceTest {

  private LlmService llmService;
  private SystemConfigRepository configRepository;
  private SkillProfileAnalysisResponseParser responseParser;
  private TagAnalysisDecisionValidator decisionValidator;
  private LlmProfileExtractionService service;

  @BeforeEach
  void setUp() {
    llmService = Mockito.mock(LlmService.class);
    configRepository = Mockito.mock(SystemConfigRepository.class);
    responseParser = Mockito.spy(new SkillProfileAnalysisResponseParser(new ObjectMapper()));
    decisionValidator = Mockito.mock(TagAnalysisDecisionValidator.class);
    when(configRepository.findValue("llm.profile_extraction.enabled")).thenReturn(Optional.of("true"));
    when(configRepository.findValue("llm.profile_extraction.fallback_to_skill")).thenReturn(Optional.of("true"));
    when(configRepository.findValue("llm.profile_extraction.system_prompt"))
        .thenReturn(Optional.of("不得夸大效果"));
    when(configRepository.findValue("llm.profile_extraction.temperature")).thenReturn(Optional.of("0.1"));
    when(configRepository.findValue("llm.profile_extraction.max_tokens")).thenReturn(Optional.of("600"));
    service = new LlmProfileExtractionService(
        llmService,
        new ProfileAnalysisPromptBuilder(),
        responseParser,
        decisionValidator,
        configRepository,
        new ObjectMapper());
  }

  @Test
  void returnsEmptyWithoutCallingLlmWhenDisabled() {
    when(configRepository.findValue("llm.profile_extraction.enabled")).thenReturn(Optional.of("false"));

    assertThat(service.tryExtract(request())).isEmpty();

    verify(llmService, never()).generateValidated(any(), any(), any(), any(), any(), any());
  }

  @Test
  void returnsStrictlyParsedAndValidatedUnifiedProfileAnalysis() {
    ProfileExtractRequest request = request();
    String raw = """
        ```json
        {
          "profile_updates": {
            "fields": {"nickname": {"value": "Alice", "confidence": "HIGH"}},
            "tag_decisions": [{
              "category_code": "custom_goal",
              "tag_codes": ["GOAL_B"],
              "confidence": 0.95,
              "evidence": "客户明确说想改善核心力量",
              "result_type": "UPDATE",
              "requested_action": "ADD"
            }]
          }
        }
        ```
        """;
    when(decisionValidator.validate(any(ProfileAnalysisResult.class), eq(request)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(llmService.generateValidated(
        eq(LlmScene.PROFILE_EXTRACTION),
        eq("TUAN_GOU"),
        eq("keeper"),
        any(),
        any(),
        any()))
        .thenAnswer(invocation -> {
          Function<String, ProfileAnalysisResult> validator = invocation.getArgument(5);
          return Optional.of(validator.apply(raw));
        });

    Optional<ProfileAnalysisResult> result = service.tryExtract(request);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().profileUpdates().fields()).containsKey("nickname");
    assertThat(result.orElseThrow().tagDecisions()).singleElement().satisfies(decision -> {
      assertThat(decision.categoryCode()).isEqualTo("custom_goal");
      assertThat(decision.tagCodes()).containsExactly("GOAL_B");
    });
    verify(responseParser).parse(org.mockito.ArgumentMatchers.contains("\"tag_decisions\""));
    verify(decisionValidator).validate(any(ProfileAnalysisResult.class), eq(request));

    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(llmService).generateValidated(
        eq(LlmScene.PROFILE_EXTRACTION),
        eq("TUAN_GOU"),
        eq("keeper"),
        any(),
        captor.capture(),
        any());
    LlmRequest llmRequest = captor.getValue();
    assertThat(llmRequest.temperature()).isEqualTo(0.1);
    assertThat(llmRequest.maxTokens()).isEqualTo(600);
    assertThat(llmRequest.systemPrompt())
        .contains("客户档案分析")
        .contains("不得夸大效果")
        .contains("profile_updates")
        .contains("tag_decisions")
        .contains("requested_action");
    assertThat(llmRequest.userPrompt())
        .contains("profile_analysis_context")
        .contains("candidateCategories")
        .contains("currentTags")
        .contains("lockedCategories")
        .contains("客户真实原话")
        .contains("员工回复内容")
        .contains("GOAL_A")
        .contains("GOAL_B")
        .contains("phoneLast4")
        .doesNotContain("18800001111")
        .doesNotContain("keeper-1");
  }

  @Test
  void returnsEmptyWhenAllRoutedResponsesFailValidation() {
    when(llmService.generateValidated(
        eq(LlmScene.PROFILE_EXTRACTION),
        eq("TUAN_GOU"),
        eq("keeper"),
        any(),
        any(),
        any()))
        .thenReturn(Optional.empty());

    assertThat(service.tryExtract(request())).isEmpty();
  }

  @Test
  void testsSelectedEnvironmentWithTheProductionProfileContractWhileDisabled() {
    ProfileExtractRequest request = request();
    LlmConfig config = new LlmConfig(
        "https://selected.example.com",
        "secret",
        "gpt-selected",
        "OPENAI_COMPATIBLE",
        12000,
        0.3,
        2048);
    when(configRepository.findValue("llm.profile_extraction.enabled")).thenReturn(Optional.of("false"));
    when(decisionValidator.validate(any(ProfileAnalysisResult.class), eq(request)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(llmService.test(any(LlmRequest.class), eq(config))).thenReturn(LlmResponse.ok("""
        {
          "profile_updates": {
            "fields": {},
            "tag_decisions": [{
              "category_code": "custom_goal",
              "tag_codes": [],
              "confidence": 0.40,
              "evidence": "当前信息不足",
              "result_type": "UNABLE_TO_DETERMINE",
              "requested_action": "NONE"
            }]
          }
        }
        """, "gpt-selected", "OPENAI_COMPATIBLE", 135L));

    LlmProfileExtractionTestResult result = service.test(request, config);

    assertThat(result.success()).isTrue();
    assertThat(result.elapsedMs()).isEqualTo(135L);
    assertThat(result.model()).isEqualTo("gpt-selected");
    assertThat(result.profileAnalysis().tagDecisions()).singleElement().satisfies(decision ->
        assertThat(decision.categoryCode()).isEqualTo("custom_goal"));
    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(llmService).test(captor.capture(), eq(config));
    assertThat(captor.getValue().systemPrompt())
        .contains("客户档案分析")
        .contains("tag_decisions");
    assertThat(captor.getValue().userPrompt())
        .contains("profile_analysis_context")
        .contains("GOAL_A")
        .contains("GOAL_B");
  }

  private ProfileExtractRequest request() {
    ProfileAnalysisContext context = new ProfileAnalysisContext(
        7L,
        4,
        1,
        List.of(
            new ProfileAnalysisContext.ConversationMessage("client", "客户真实原话", "12:00"),
            new ProfileAnalysisContext.ConversationMessage("keeper", "员工回复内容", "12:01")),
        Map.of("nickname", "测试客户", "phoneLast4", "1111", "leadType", "TUAN_GOU"),
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
            1,
            12,
            "KEEP_CURRENT",
            List.of(
                new ProfileAnalysisContext.TagCandidate(
                    "GOAL_A", "目标 A", "含义 A", "适用 A", "禁止 A", "正例 A", "反例 A", List.of()),
                new ProfileAnalysisContext.TagCandidate(
                    "GOAL_B", "目标 B", "含义 B", "适用 B", "禁止 B", "正例 B", "反例 B", List.of())))));
    return new ProfileExtractRequest(
        "旧摘要不应覆盖结构化上下文",
        Map.of("phone", "18800001111", "leadType", "TUAN_GOU"),
        List.of("nickname"),
        "keeper",
        context);
  }
}
