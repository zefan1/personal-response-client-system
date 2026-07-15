package com.privateflow.modules.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.privateflow.modules.profile.service.TagAnalysisDecisionValidator;
import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.ProfileUpdates;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillErrorCodes;
import com.privateflow.modules.skill.SkillGatewayException;
import com.privateflow.modules.skill.circuit.SkillCircuitBreaker;
import com.privateflow.modules.skill.client.SkillHttpClient;
import com.privateflow.modules.skill.config.SkillConfig;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import com.privateflow.modules.skill.health.SkillHealthMonitor;
import com.privateflow.modules.skill.infra.SkillCallLogger;
import com.privateflow.modules.skill.parser.SkillProfileAnalysisResponseParser;
import com.privateflow.modules.skill.parser.SkillResponseParser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillGatewayServiceImplProfileAnalysisTest {

  private SkillRequestBuilder requestBuilder;
  private SkillHttpClient httpClient;
  private SkillProfileAnalysisResponseParser profileParser;
  private TagAnalysisDecisionValidator decisionValidator;
  private SkillCircuitBreaker circuitBreaker;
  private SkillCallLogger callLogger;
  private SkillHealthMonitor healthMonitor;
  private SkillGatewayServiceImpl service;

  @BeforeEach
  void setUp() {
    requestBuilder = mock(SkillRequestBuilder.class);
    httpClient = mock(SkillHttpClient.class);
    profileParser = mock(SkillProfileAnalysisResponseParser.class);
    decisionValidator = mock(TagAnalysisDecisionValidator.class);
    circuitBreaker = mock(SkillCircuitBreaker.class);
    callLogger = mock(SkillCallLogger.class);
    healthMonitor = mock(SkillHealthMonitor.class);
    SkillConfigProvider configProvider = mock(SkillConfigProvider.class);
    when(configProvider.get()).thenReturn(config());
    when(circuitBreaker.allowRequest(Scene.PROFILE_EXTRACT)).thenReturn(true);
    service = new SkillGatewayServiceImpl(
        requestBuilder,
        httpClient,
        mock(SkillResponseParser.class),
        profileParser,
        decisionValidator,
        circuitBreaker,
        mock(SkillFallbackHandler.class),
        callLogger,
        healthMonitor,
        configProvider);
  }

  @Test
  void returnsOnlyStrictlyParsedAndValidatedProfileAnalysis() {
    ProfileExtractRequest request = request();
    ProfileAnalysisResult parsed = new ProfileAnalysisResult(ProfileUpdates.empty(), List.of());
    ProfileAnalysisResult validated = new ProfileAnalysisResult(ProfileUpdates.empty(), List.of());
    when(requestBuilder.requestSummary("客户真实原话", null)).thenReturn("summary");
    when(requestBuilder.buildProfileExtract(request)).thenReturn(new HashMap<>(Map.of("scene", "PROFILE_EXTRACT")));
    when(httpClient.call(any(), eq(8000))).thenReturn("raw");
    when(profileParser.parse("raw")).thenReturn(parsed);
    when(decisionValidator.validate(parsed, request)).thenReturn(validated);

    ProfileAnalysisResult result = service.extractProfile(request);

    assertThat(result).isSameAs(validated);
    verify(circuitBreaker).recordSuccess(Scene.PROFILE_EXTRACT);
    verify(healthMonitor).record(true);
    verify(callLogger).logCall(eq(Scene.PROFILE_EXTRACT), eq(null), eq("keeper-1"), eq("summary"), any(Long.class), eq(true), eq(null));
  }

  @Test
  void invalidProfileAnalysisRecordsFailureAndReturnsNoChanges() {
    ProfileExtractRequest request = request();
    when(requestBuilder.requestSummary("客户真实原话", null)).thenReturn("summary");
    when(requestBuilder.buildProfileExtract(request)).thenReturn(new HashMap<>(Map.of("scene", "PROFILE_EXTRACT")));
    when(httpClient.call(any(), eq(8000))).thenReturn("legacy");
    when(profileParser.parse("legacy")).thenThrow(new SkillGatewayException(
        SkillErrorCodes.SKILL_RESPONSE_INVALID,
        "invalid",
        true));

    ProfileAnalysisResult result = service.extractProfile(request);

    assertThat(result.profileUpdates().fields()).isEmpty();
    assertThat(result.tagDecisions()).isEmpty();
    verify(circuitBreaker).recordFailure(Scene.PROFILE_EXTRACT);
    verify(healthMonitor).record(false);
    verify(callLogger).logCall(eq(Scene.PROFILE_EXTRACT), eq(null), eq("keeper-1"), eq("summary"), any(Long.class), eq(false), any());
  }

  @Test
  void openProfileCircuitLogsFailureAndReturnsNoChangesWithoutExternalCall() {
    ProfileExtractRequest request = request();
    when(circuitBreaker.allowRequest(Scene.PROFILE_EXTRACT)).thenReturn(false);
    when(requestBuilder.requestSummary("客户真实原话", null)).thenReturn("summary");

    ProfileAnalysisResult result = service.extractProfile(request);

    assertThat(result).isEqualTo(ProfileAnalysisResult.empty());
    verify(healthMonitor).record(false);
    verify(callLogger).logCall(
        eq(Scene.PROFILE_EXTRACT), eq(null), eq("keeper-1"), eq("summary"), any(Long.class), eq(false), any());
    verifyNoInteractions(httpClient, profileParser, decisionValidator);
  }

  @Test
  void timeoutRecordsProfileFailureAndReturnsNoChanges() {
    ProfileExtractRequest request = request();
    when(requestBuilder.requestSummary("客户真实原话", null)).thenReturn("summary");
    when(requestBuilder.buildProfileExtract(request)).thenReturn(new HashMap<>(Map.of("scene", "PROFILE_EXTRACT")));
    when(httpClient.call(any(), eq(8000))).thenThrow(new SkillGatewayException(
        SkillErrorCodes.SKILL_TIMEOUT,
        "timeout",
        true));

    ProfileAnalysisResult result = service.extractProfile(request);

    assertThat(result).isEqualTo(ProfileAnalysisResult.empty());
    verify(circuitBreaker).recordFailure(Scene.PROFILE_EXTRACT);
    verify(healthMonitor).record(false);
    verify(callLogger).logCall(
        eq(Scene.PROFILE_EXTRACT), eq(null), eq("keeper-1"), eq("summary"), any(Long.class), eq(false), any());
  }

  private ProfileExtractRequest request() {
    return new ProfileExtractRequest(
        "客户真实原话",
        Map.of("leadType", "TUAN_GOU"),
        List.of(),
        "keeper-1");
  }

  private SkillConfig config() {
    return new SkillConfig(
        "http://localhost", "key", "LAST_FOUR", "", 1000, 30, 0.5, 5, 30,
        "fallback", "", "", "", "prompt", "", 0.3, 15, 8000, 3);
  }
}
