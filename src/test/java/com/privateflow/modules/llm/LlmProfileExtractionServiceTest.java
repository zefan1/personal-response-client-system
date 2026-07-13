package com.privateflow.modules.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.ProfileUpdates;
import com.privateflow.modules.skill.parser.SkillResponseParser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class LlmProfileExtractionServiceTest {

  private LlmService llmService;
  private SystemConfigRepository configRepository;
  private LlmProfileExtractionService service;

  @BeforeEach
  void setUp() {
    llmService = Mockito.mock(LlmService.class);
    configRepository = Mockito.mock(SystemConfigRepository.class);
    when(configRepository.findValue("llm.profile_extraction.enabled")).thenReturn(Optional.of("true"));
    when(configRepository.findValue("llm.profile_extraction.fallback_to_skill")).thenReturn(Optional.of("true"));
    when(configRepository.findValue("llm.profile_extraction.system_prompt")).thenReturn(Optional.of("Return profile_updates JSON only."));
    when(configRepository.findValue("llm.profile_extraction.temperature")).thenReturn(Optional.of("0.1"));
    when(configRepository.findValue("llm.profile_extraction.max_tokens")).thenReturn(Optional.of("600"));
    ObjectMapper objectMapper = new ObjectMapper();
    service = new LlmProfileExtractionService(
        llmService,
        new SkillResponseParser(objectMapper),
        configRepository,
        objectMapper);
  }

  @Test
  void returnsEmptyWhenDisabled() {
    when(configRepository.findValue("llm.profile_extraction.enabled")).thenReturn(Optional.of("false"));

    assertThat(service.tryExtract(request())).isEmpty();
  }

  @Test
  void parsesProfileUpdatesAndUsesProfileExtractionScene() {
    when(llmService.generate(eq(LlmScene.PROFILE_EXTRACTION), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.ok("""
            ```json
            {"profile_updates":{"fields":{
              "bodyConcerns":{"value":"腹直肌分离","confidence":"HIGH"},
              "intentLevel":{"value":"HIGH","confidence":"MEDIUM"}
            }}}
            ```
            """, "gpt-4.1-mini", "OPENAI_COMPATIBLE", 98));

    Optional<ProfileUpdates> updates = service.tryExtract(request());

    assertThat(updates).isPresent();
    assertThat(updates.orElseThrow().fields()).containsKeys("bodyConcerns", "intentLevel");
    assertThat(updates.orElseThrow().fields().get("bodyConcerns").value()).isEqualTo("腹直肌分离");
    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(llmService).generate(eq(LlmScene.PROFILE_EXTRACTION), eq("TUAN_GOU"), eq("keeper"), any(), captor.capture());
    assertThat(captor.getValue().temperature()).isEqualTo(0.1);
    assertThat(captor.getValue().maxTokens()).isEqualTo(600);
    assertThat(captor.getValue().userPrompt()).doesNotContain("18800001111");
    assertThat(captor.getValue().userPrompt()).contains("1111");
    assertThat(captor.getValue().userPrompt()).contains("bodyConcerns");
  }

  @Test
  void returnsEmptyWhenLlmFailsOrResponseHasNoUpdates() {
    when(llmService.generate(eq(LlmScene.PROFILE_EXTRACTION), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.failed(LlmErrorCodes.CONFIG_MISSING, "missing", "", "OPENAI_COMPATIBLE", 1));

    assertThat(service.tryExtract(request())).isEmpty();

    when(llmService.generate(eq(LlmScene.PROFILE_EXTRACTION), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.ok("{}", "gpt", "OPENAI_COMPATIBLE", 1));

    assertThat(service.tryExtract(request())).isEmpty();
  }

  private ProfileExtractRequest request() {
    return new ProfileExtractRequest(
        "客户说她产后 6 个月，主要担心腹直肌分离。",
        Map.of(
            "phone", "18800001111",
            "leadType", "TUAN_GOU",
            "nickname", "测试客户"),
        List.of("bodyConcerns", "intentLevel"),
        "keeper");
  }
}
