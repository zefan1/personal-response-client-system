package com.privateflow.modules.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.privateflow.modules.profile.service.TagAnalysisDecisionValidator;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.circuit.SkillCircuitBreaker;
import com.privateflow.modules.skill.client.SkillHttpClient;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import com.privateflow.modules.skill.health.SkillHealthMonitor;
import com.privateflow.modules.skill.infra.SkillCallLogger;
import com.privateflow.modules.skill.parser.SkillProfileAnalysisResponseParser;
import com.privateflow.modules.skill.parser.SkillResponseParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillGatewayServiceImplCircuitTest {

  @Test
  void openReplyCircuitLogsFailureAndReturnsFallbackWithoutExternalCall() {
    SkillRequestBuilder requestBuilder = mock(SkillRequestBuilder.class);
    SkillHttpClient httpClient = mock(SkillHttpClient.class);
    SkillCircuitBreaker circuitBreaker = mock(SkillCircuitBreaker.class);
    SkillFallbackHandler fallbackHandler = mock(SkillFallbackHandler.class);
    SkillCallLogger callLogger = mock(SkillCallLogger.class);
    SkillHealthMonitor healthMonitor = mock(SkillHealthMonitor.class);
    SkillResponse fallback = mock(SkillResponse.class);
    SkillGatewayServiceImpl service = new SkillGatewayServiceImpl(
        requestBuilder,
        httpClient,
        mock(SkillResponseParser.class),
        mock(SkillProfileAnalysisResponseParser.class),
        mock(TagAnalysisDecisionValidator.class),
        circuitBreaker,
        fallbackHandler,
        callLogger,
        healthMonitor,
        mock(SkillConfigProvider.class));
    SkillRequest request = new SkillRequest(
        Scene.ACTIVE_REPLY, "TUAN_GOU", null, "hello", Map.of(), Map.of(), List.of(), List.of(), "keeper-1");
    when(requestBuilder.requestSummary("hello", null)).thenReturn("summary");
    when(circuitBreaker.allowRequest(Scene.ACTIVE_REPLY)).thenReturn(false);
    when(fallbackHandler.fallback()).thenReturn(fallback);

    SkillResponse result = service.generateReplies(request);

    assertThat(result).isSameAs(fallback);
    verify(healthMonitor).record(false);
    verify(callLogger).logCall(
        eq(Scene.ACTIVE_REPLY), eq("TUAN_GOU"), eq("keeper-1"), eq("summary"), any(Long.class), eq(false), any());
    verifyNoInteractions(httpClient);
  }
}
