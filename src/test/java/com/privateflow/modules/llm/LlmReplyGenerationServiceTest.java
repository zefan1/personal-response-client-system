package com.privateflow.modules.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.ReplyTagSnapshot;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.parser.SkillResponseParser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class LlmReplyGenerationServiceTest {

  private LlmService llmService;
  private SystemConfigRepository configRepository;
  private LlmReplyGenerationService service;

  @BeforeEach
  void setUp() {
    llmService = Mockito.mock(LlmService.class);
    configRepository = Mockito.mock(SystemConfigRepository.class);
    when(configRepository.findValue("llm.reply_generation.enabled")).thenReturn(Optional.of("true"));
    when(configRepository.findValue("llm.reply_generation.fallback_to_skill")).thenReturn(Optional.of("true"));
    when(configRepository.findValue("llm.reply_generation.system_prompt")).thenReturn(Optional.of("Return JSON only."));
    when(configRepository.findValue("llm.reply_generation.temperature")).thenReturn(Optional.of("0.4"));
    when(configRepository.findValue("llm.reply_generation.max_tokens")).thenReturn(Optional.of("777"));
    service = new LlmReplyGenerationService(
        llmService,
        new SkillResponseParser(new ObjectMapper()),
        configRepository,
        new ObjectMapper());
  }

  @Test
  void returnsEmptyWhenDisabled() {
    when(configRepository.findValue("llm.reply_generation.enabled")).thenReturn(Optional.of("false"));

    assertThat(service.tryGenerate(request())).isEmpty();
  }

  @Test
  void parsesLlmJsonReplyAndUsesReplyGenerationScene() {
    when(llmService.generate(eq(LlmScene.REPLY_GENERATION), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.ok("""
            ```json
            {"suggestions":[
              {"text":"建议先确认她目前恢复月份，再邀请到店评估。","direction":"NEXT_STEP","reason":"先确认需求"},
              {"text":"可以温和说明评估会更准确。","direction":"ANSWER","reason":"降低顾虑"},
              {"text":"如果方便，我帮你约一个到店时间。","direction":"SOFT_CLOSE","reason":"推动下一步"}
            ]}
            ```
            """, "gpt-4.1-mini", "OPENAI_COMPATIBLE", 120));

    Optional<SkillResponse> response = service.tryGenerate(request());

    assertThat(response).isPresent();
    assertThat(response.orElseThrow().suggestions()).hasSize(3);
    assertThat(response.orElseThrow().suggestions().get(0).text()).contains("恢复月份");
    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(llmService).generate(eq(LlmScene.REPLY_GENERATION), eq("TUAN_GOU"), eq("keeper"), any(), captor.capture());
    assertThat(captor.getValue().temperature()).isEqualTo(0.4);
    assertThat(captor.getValue().maxTokens()).isEqualTo(777);
    assertThat(captor.getValue().userPrompt()).doesNotContain("18800001111");
    assertThat(captor.getValue().userPrompt()).contains("1111");
  }

  @Test
  void includesCurrentReplyTagsAndNonDisclosureGuidanceInPrompt() {
    when(llmService.generate(eq(LlmScene.REPLY_GENERATION), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.failed(LlmErrorCodes.CONFIG_MISSING, "missing", "", "OPENAI_COMPATIBLE", 1));
    ReplyTagSnapshot tag = new ReplyTagSnapshot(
        "personality_type",
        "性格类型",
        "LOYALIST",
        "忠诚型",
        "重视安全感",
        "MANUAL",
        "客户证据",
        true);
    SkillRequest request = new SkillRequest(
        Scene.ACTIVE_REPLY,
        "TUAN_GOU",
        "18800001111",
        "hello",
        Map.of("nickname", "Alice", "phone", "18800001111"),
        Map.of(),
        List.of(),
        List.of(),
        "keeper",
        List.of(tag));

    assertThat(service.tryGenerate(request)).isEmpty();

    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(llmService).generate(eq(LlmScene.REPLY_GENERATION), eq("TUAN_GOU"), eq("keeper"), any(), captor.capture());
    assertThat(captor.getValue().userPrompt())
        .contains("currentTags")
        .contains("忠诚型")
        .contains("重视安全感")
        .contains("标签只用于调整回复方向")
        .doesNotContain("18800001111");
    assertThat(captor.getValue().systemPrompt())
        .contains("标签只用于调整回复方向")
        .contains("不得向客户描述内部标签");
  }

  @Test
  void returnsEmptyWhenLlmFailsOrResponseCannotParse() {
    when(llmService.generate(eq(LlmScene.REPLY_GENERATION), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.failed(LlmErrorCodes.CONFIG_MISSING, "missing", "", "OPENAI_COMPATIBLE", 1));

    assertThat(service.tryGenerate(request())).isEmpty();

    when(llmService.generate(eq(LlmScene.REPLY_GENERATION), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.ok("not json", "gpt", "OPENAI_COMPATIBLE", 1));

    assertThat(service.tryGenerate(request())).isEmpty();
  }

  private SkillRequest request() {
    return new SkillRequest(
        Scene.ACTIVE_REPLY,
        "TUAN_GOU",
        "18800001111",
        "客户问产后修复有没有必要",
        Map.of("nickname", "测试客户", "phone", "18800001111", "followupNotes", "关注腹直肌"),
        Map.of(),
        List.of("旧建议"),
        List.of(Map.of("role", "client", "text", "我想了解腹直肌修复")),
        "keeper");
  }
}
