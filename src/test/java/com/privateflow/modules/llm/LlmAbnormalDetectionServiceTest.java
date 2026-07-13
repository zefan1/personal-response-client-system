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

class LlmAbnormalDetectionServiceTest {

  private LlmService llmService;
  private SystemConfigRepository configRepository;
  private LlmAbnormalDetectionService service;

  @BeforeEach
  void setUp() {
    llmService = Mockito.mock(LlmService.class);
    configRepository = Mockito.mock(SystemConfigRepository.class);
    when(configRepository.findValue("llm.abnormal_detection.enabled")).thenReturn(Optional.of("true"));
    when(configRepository.findValue("llm.abnormal_detection.system_prompt")).thenReturn(Optional.of("Return abnormal_alert JSON only."));
    when(configRepository.findValue("llm.abnormal_detection.temperature")).thenReturn(Optional.of("0.1"));
    when(configRepository.findValue("llm.abnormal_detection.max_tokens")).thenReturn(Optional.of("500"));
    service = new LlmAbnormalDetectionService(llmService, configRepository, new ObjectMapper());
  }

  @Test
  void returnsEmptyWhenDisabled() {
    when(configRepository.findValue("llm.abnormal_detection.enabled")).thenReturn(Optional.of("false"));

    assertThat(service.tryDetect(input())).isEmpty();
  }

  @Test
  void parsesTriggeredAlertAndUsesAbnormalScene() {
    when(llmService.generate(eq(LlmScene.ABNORMAL_DETECTION), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.ok("""
            ```json
            {"abnormal_alert":{"triggered":true,"alert_type":"CUSTOMER_COMPLAINT","level":"WARN","message":"客户表达强烈不满，需要及时安抚"}}
            ```
            """, "gpt-4.1-mini", "OPENAI_COMPATIBLE", 90));

    Optional<LlmAbnormalAlert> result = service.tryDetect(input());

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().alertType()).isEqualTo("CUSTOMER_COMPLAINT");
    assertThat(result.orElseThrow().level()).isEqualTo("WARN");
    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(llmService).generate(eq(LlmScene.ABNORMAL_DETECTION), eq("TUAN_GOU"), eq("keeper"), any(), captor.capture());
    assertThat(captor.getValue().temperature()).isEqualTo(0.1);
    assertThat(captor.getValue().maxTokens()).isEqualTo(500);
    assertThat(captor.getValue().userPrompt()).doesNotContain("18800001111");
    assertThat(captor.getValue().userPrompt()).contains("1111");
  }

  @Test
  void returnsEmptyWhenNotTriggeredOrInvalid() {
    when(llmService.generate(eq(LlmScene.ABNORMAL_DETECTION), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.ok("{\"abnormal_alert\":{\"triggered\":false}}", "gpt", "OPENAI_COMPATIBLE", 1));

    assertThat(service.tryDetect(input())).isEmpty();

    when(llmService.generate(eq(LlmScene.ABNORMAL_DETECTION), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.ok("{\"abnormal_alert\":{\"triggered\":true,\"alert_type\":\"BAD\",\"level\":\"WARN\",\"message\":\"x\"}}", "gpt", "OPENAI_COMPATIBLE", 1));

    assertThat(service.tryDetect(input())).isEmpty();
  }

  private LlmAbnormalDetectionInput input() {
    return new LlmAbnormalDetectionInput(
        "18800001111",
        "Alice",
        "TUAN_GOU",
        "客户说很不满意，要投诉",
        List.of(new CustomerMessageSentEvent.ChatMessage("client", "你们服务太差了，我要投诉", "12:00")),
        "我马上帮您处理",
        "keeper");
  }
}
