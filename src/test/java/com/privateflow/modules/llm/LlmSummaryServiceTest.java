package com.privateflow.modules.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class LlmSummaryServiceTest {

  private LlmService llmService;
  private SystemConfigRepository configRepository;
  private LlmSummaryService service;

  @BeforeEach
  void setUp() {
    llmService = Mockito.mock(LlmService.class);
    configRepository = Mockito.mock(SystemConfigRepository.class);
    when(configRepository.findValue("llm.summary.enabled")).thenReturn(Optional.of("true"));
    when(configRepository.findValue("llm.summary.system_prompt")).thenReturn(Optional.of("Return summary JSON only."));
    when(configRepository.findValue("llm.summary.temperature")).thenReturn(Optional.of("0.2"));
    when(configRepository.findValue("llm.summary.max_tokens")).thenReturn(Optional.of("500"));
    service = new LlmSummaryService(llmService, configRepository, new ObjectMapper());
  }

  @Test
  void returnsEmptyWhenDisabled() {
    when(configRepository.findValue("llm.summary.enabled")).thenReturn(Optional.of("false"));

    assertThat(service.trySummarize(input())).isEmpty();
  }

  @Test
  void parsesSummaryAndUsesSummaryScene() {
    when(llmService.generate(eq(LlmScene.SUMMARY), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.ok("""
            ```json
            {"summary":"客户关注腹直肌修复，表示需要考虑，约定明天确认到店时间"}
            ```
            """, "gpt-4.1-mini", "OPENAI_COMPATIBLE", 70));

    Optional<String> result = service.trySummarize(input());

    assertThat(result).contains("客户关注腹直肌修复，表示需要考虑，约定明天确认到店时间");
    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(llmService).generate(eq(LlmScene.SUMMARY), eq("TUAN_GOU"), eq("keeper"), any(), captor.capture());
    assertThat(captor.getValue().temperature()).isEqualTo(0.2);
    assertThat(captor.getValue().maxTokens()).isEqualTo(500);
    assertThat(captor.getValue().userPrompt()).doesNotContain("18800001111");
    assertThat(captor.getValue().userPrompt()).contains("1111");
  }

  @Test
  void returnsEmptyWhenLlmFailsOrResponseCannotParse() {
    when(llmService.generate(eq(LlmScene.SUMMARY), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.failed(LlmErrorCodes.CONFIG_MISSING, "missing", "", "OPENAI_COMPATIBLE", 1));

    assertThat(service.trySummarize(input())).isEmpty();

    when(llmService.generate(eq(LlmScene.SUMMARY), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.ok("{}", "gpt", "OPENAI_COMPATIBLE", 1));

    assertThat(service.trySummarize(input())).isEmpty();
  }

  private LlmSummaryInput input() {
    return new LlmSummaryInput(
        "18800001111",
        "Alice",
        "TUAN_GOU",
        List.of(new CustomerMessageSentEvent.ChatMessage("client", "我想了解腹直肌修复", "12:00")),
        "好的，我明天再确认",
        "NEXT_STEP",
        "keeper");
  }
}
