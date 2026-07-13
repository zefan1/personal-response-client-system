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

class LlmFollowupSuggestionServiceTest {

  private LlmService llmService;
  private SystemConfigRepository configRepository;
  private LlmFollowupSuggestionService service;

  @BeforeEach
  void setUp() {
    llmService = Mockito.mock(LlmService.class);
    configRepository = Mockito.mock(SystemConfigRepository.class);
    when(configRepository.findValue("llm.followup_suggestion.enabled")).thenReturn(Optional.of("true"));
    when(configRepository.findValue("llm.followup_suggestion.system_prompt")).thenReturn(Optional.of("Return followup_suggest JSON only."));
    when(configRepository.findValue("llm.followup_suggestion.temperature")).thenReturn(Optional.of("0.2"));
    when(configRepository.findValue("llm.followup_suggestion.max_tokens")).thenReturn(Optional.of("500"));
    service = new LlmFollowupSuggestionService(llmService, configRepository, new ObjectMapper());
  }

  @Test
  void returnsEmptyWhenDisabled() {
    when(configRepository.findValue("llm.followup_suggestion.enabled")).thenReturn(Optional.of("false"));

    assertThat(service.trySuggest(input())).isEmpty();
  }

  @Test
  void parsesFollowupSuggestionAndUsesFollowupScene() {
    when(llmService.generate(eq(LlmScene.FOLLOWUP_SUGGESTION), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.ok("""
            ```json
            {"followup_suggest":{"next_contact_at":"2026-07-11T09:00:00","next_contact_direction":"确认到店评估时间"}}
            ```
            """, "gpt-4.1-mini", "OPENAI_COMPATIBLE", 80));

    Optional<CustomerMessageSentEvent.FollowupSuggestPayload> result = service.trySuggest(input());

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().nextFollowupAt()).isEqualTo("2026-07-11T09:00");
    assertThat(result.orElseThrow().nextFollowupDir()).isEqualTo("确认到店评估时间");
    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(llmService).generate(eq(LlmScene.FOLLOWUP_SUGGESTION), eq("TUAN_GOU"), eq("keeper"), any(), captor.capture());
    assertThat(captor.getValue().temperature()).isEqualTo(0.2);
    assertThat(captor.getValue().maxTokens()).isEqualTo(500);
    assertThat(captor.getValue().userPrompt()).doesNotContain("18800001111");
    assertThat(captor.getValue().userPrompt()).contains("1111");
  }

  @Test
  void returnsEmptyWhenLlmFailsOrResponseCannotParse() {
    when(llmService.generate(eq(LlmScene.FOLLOWUP_SUGGESTION), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.failed(LlmErrorCodes.CONFIG_MISSING, "missing", "", "OPENAI_COMPATIBLE", 1));

    assertThat(service.trySuggest(input())).isEmpty();

    when(llmService.generate(eq(LlmScene.FOLLOWUP_SUGGESTION), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.ok("{}", "gpt", "OPENAI_COMPATIBLE", 1));

    assertThat(service.trySuggest(input())).isEmpty();
  }

  private LlmFollowupSuggestionInput input() {
    return new LlmFollowupSuggestionInput(
        "18800001111",
        "Alice",
        "TUAN_GOU",
        "客户说要考虑一下",
        List.of(new CustomerMessageSentEvent.ChatMessage("client", "我再考虑一下", "12:00")),
        "好的，我明天再确认",
        "NEXT_STEP",
        "keeper");
  }
}
