package com.privateflow.modules.skill.service;

import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillErrorCodes;
import com.privateflow.modules.skill.SkillGatewayException;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.skill.SkillGatewayService;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.circuit.SkillCircuitBreaker;
import com.privateflow.modules.skill.client.SkillHttpClient;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import com.privateflow.modules.skill.health.SkillHealthMonitor;
import com.privateflow.modules.skill.infra.SkillCallLogger;
import com.privateflow.modules.skill.parser.SkillProfileAnalysisResponseParser;
import com.privateflow.modules.skill.parser.SkillResponseParser;
import com.privateflow.modules.profile.service.TagAnalysisDecisionValidator;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SkillGatewayServiceImpl implements SkillGatewayService {

  private final SkillRequestBuilder requestBuilder;
  private final SkillHttpClient httpClient;
  private final SkillResponseParser responseParser;
  private final SkillProfileAnalysisResponseParser profileAnalysisResponseParser;
  private final TagAnalysisDecisionValidator tagAnalysisDecisionValidator;
  private final SkillCircuitBreaker circuitBreaker;
  private final SkillFallbackHandler fallbackHandler;
  private final SkillCallLogger callLogger;
  private final SkillHealthMonitor healthMonitor;
  private final SkillConfigProvider configProvider;

  public SkillGatewayServiceImpl(
      SkillRequestBuilder requestBuilder,
      SkillHttpClient httpClient,
      SkillResponseParser responseParser,
      SkillProfileAnalysisResponseParser profileAnalysisResponseParser,
      TagAnalysisDecisionValidator tagAnalysisDecisionValidator,
      SkillCircuitBreaker circuitBreaker,
      SkillFallbackHandler fallbackHandler,
      SkillCallLogger callLogger,
      SkillHealthMonitor healthMonitor,
      SkillConfigProvider configProvider) {
    this.requestBuilder = requestBuilder;
    this.httpClient = httpClient;
    this.responseParser = responseParser;
    this.profileAnalysisResponseParser = profileAnalysisResponseParser;
    this.tagAnalysisDecisionValidator = tagAnalysisDecisionValidator;
    this.circuitBreaker = circuitBreaker;
    this.fallbackHandler = fallbackHandler;
    this.callLogger = callLogger;
    this.healthMonitor = healthMonitor;
    this.configProvider = configProvider;
  }

  @Override
  public SkillResponse generateReplies(SkillRequest request) {
    SkillRequest actual = request.scene() == null ? new SkillRequest(Scene.CHAT_RECOGNIZE, request.leadType(), request.phone(),
        request.clientMessage(), request.customer(), request.systemPrompt(), request.previousSuggestions(), request.chatContext(), request.caller()) : request;
    Scene scene = actual.scene();
    String summary = requestBuilder.requestSummary(actual.clientMessage(), actual.phone());
    long start = System.currentTimeMillis();
    if (!circuitBreaker.allowRequest(scene)) {
      recordOpenCircuit(scene, actual.leadType(), actual.caller(), summary, start);
      return fallbackHandler.fallback();
    }
    try {
      Map<String, Object> payload = requestBuilder.build(actual);
      String raw = httpClient.call(payload, configProvider.get().timeoutMs());
      SkillResponse response = responseParser.parseReplies(raw);
      circuitBreaker.recordSuccess(scene);
      healthMonitor.record(true);
      callLogger.logCall(actual.scene(), actual.leadType(), actual.caller(), summary, elapsed(start), true, null);
      return response;
    } catch (SkillGatewayException ex) {
      if (ex.isCircuitFailure()) {
        circuitBreaker.recordFailure(scene);
      }
      healthMonitor.record(false);
      callLogger.logCall(actual.scene(), actual.leadType(), actual.caller(), summary, elapsed(start), false, ex.getErrorCode() + " " + ex.getMessage());
      if (!ex.isFallbackAllowed()) {
        throw new ApiException(ex.getErrorCode(), ex.getMessage());
      }
      return fallbackHandler.fallback();
    } catch (RuntimeException ex) {
      circuitBreaker.recordFailure(scene);
      healthMonitor.record(false);
      callLogger.logCall(actual.scene(), actual.leadType(), actual.caller(), summary, elapsed(start), false, ex.getMessage());
      return fallbackHandler.fallback();
    }
  }

  @Override
  public ProfileAnalysisResult extractProfile(ProfileExtractRequest request) {
    long start = System.currentTimeMillis();
    String summary = requestBuilder.requestSummary(request.conversationText(), null);
    if (!circuitBreaker.allowRequest(Scene.PROFILE_EXTRACT)) {
      recordOpenCircuit(Scene.PROFILE_EXTRACT, null, request.caller(), summary, start);
      return ProfileAnalysisResult.empty();
    }
    try {
      Map<String, Object> payload = requestBuilder.buildProfileExtract(request);
      String raw = httpClient.call(payload, configProvider.get().profileExtractTimeoutMs());
      ProfileAnalysisResult analysis = tagAnalysisDecisionValidator.validate(
          profileAnalysisResponseParser.parse(raw),
          request);
      circuitBreaker.recordSuccess(Scene.PROFILE_EXTRACT);
      healthMonitor.record(true);
      callLogger.logCall(Scene.PROFILE_EXTRACT, null, request.caller(), summary, elapsed(start), true, null);
      return analysis;
    } catch (SkillGatewayException ex) {
      if (ex.isCircuitFailure()) {
        circuitBreaker.recordFailure(Scene.PROFILE_EXTRACT);
      }
      healthMonitor.record(false);
      callLogger.logCall(Scene.PROFILE_EXTRACT, null, request.caller(), summary, elapsed(start), false, ex.getErrorCode() + " " + ex.getMessage());
      return ProfileAnalysisResult.empty();
    } catch (RuntimeException ex) {
      circuitBreaker.recordFailure(Scene.PROFILE_EXTRACT);
      healthMonitor.record(false);
      callLogger.logCall(Scene.PROFILE_EXTRACT, null, request.caller(), summary, elapsed(start), false, ex.getMessage());
      return ProfileAnalysisResult.empty();
    }
  }

  private void recordOpenCircuit(
      Scene scene,
      String leadType,
      String caller,
      String summary,
      long start) {
    healthMonitor.record(false);
    callLogger.logCall(
        scene,
        leadType,
        caller,
        summary,
        elapsed(start),
        false,
        SkillErrorCodes.SKILL_UNREACHABLE + " Skill circuit open");
  }

  private long elapsed(long start) {
    return System.currentTimeMillis() - start;
  }
}
