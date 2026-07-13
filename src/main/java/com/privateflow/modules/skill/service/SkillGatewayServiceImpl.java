package com.privateflow.modules.skill.service;

import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.ProfileUpdates;
import com.privateflow.modules.skill.Scene;
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
import com.privateflow.modules.skill.parser.SkillResponseParser;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SkillGatewayServiceImpl implements SkillGatewayService {

  private final SkillRequestBuilder requestBuilder;
  private final SkillHttpClient httpClient;
  private final SkillResponseParser responseParser;
  private final SkillCircuitBreaker circuitBreaker;
  private final SkillFallbackHandler fallbackHandler;
  private final SkillCallLogger callLogger;
  private final SkillHealthMonitor healthMonitor;
  private final SkillConfigProvider configProvider;

  public SkillGatewayServiceImpl(
      SkillRequestBuilder requestBuilder,
      SkillHttpClient httpClient,
      SkillResponseParser responseParser,
      SkillCircuitBreaker circuitBreaker,
      SkillFallbackHandler fallbackHandler,
      SkillCallLogger callLogger,
      SkillHealthMonitor healthMonitor,
      SkillConfigProvider configProvider) {
    this.requestBuilder = requestBuilder;
    this.httpClient = httpClient;
    this.responseParser = responseParser;
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
    String summary = requestBuilder.requestSummary(actual.clientMessage(), actual.phone());
    long start = System.currentTimeMillis();
    if (!circuitBreaker.allowRequest()) {
      return fallbackHandler.fallback();
    }
    try {
      Map<String, Object> payload = requestBuilder.build(actual);
      String raw = httpClient.call(payload, configProvider.get().timeoutMs());
      SkillResponse response = responseParser.parseReplies(raw);
      circuitBreaker.recordSuccess();
      healthMonitor.record(true);
      callLogger.logCall(actual.scene(), actual.leadType(), actual.caller(), summary, elapsed(start), true, null);
      return response;
    } catch (SkillGatewayException ex) {
      if (ex.isCircuitFailure()) {
        circuitBreaker.recordFailure();
      }
      healthMonitor.record(false);
      callLogger.logCall(actual.scene(), actual.leadType(), actual.caller(), summary, elapsed(start), false, ex.getErrorCode() + " " + ex.getMessage());
      if (!ex.isFallbackAllowed()) {
        throw new ApiException(ex.getErrorCode(), ex.getMessage());
      }
      return fallbackHandler.fallback();
    } catch (RuntimeException ex) {
      circuitBreaker.recordFailure();
      healthMonitor.record(false);
      callLogger.logCall(actual.scene(), actual.leadType(), actual.caller(), summary, elapsed(start), false, ex.getMessage());
      return fallbackHandler.fallback();
    }
  }

  @Override
  public ProfileUpdates extractProfile(ProfileExtractRequest request) {
    long start = System.currentTimeMillis();
    String summary = requestBuilder.requestSummary(request.conversationText(), null);
    if (!circuitBreaker.allowRequest()) {
      return ProfileUpdates.empty();
    }
    try {
      Map<String, Object> payload = requestBuilder.buildProfileExtract(request);
      String raw = httpClient.call(payload, configProvider.get().profileExtractTimeoutMs());
      ProfileUpdates updates = responseParser.parseProfileUpdatesOnly(raw);
      circuitBreaker.recordSuccess();
      healthMonitor.record(true);
      callLogger.logCall(Scene.PROFILE_EXTRACT, null, request.caller(), summary, elapsed(start), true, null);
      return updates;
    } catch (SkillGatewayException ex) {
      if (ex.isCircuitFailure()) {
        circuitBreaker.recordFailure();
      }
      healthMonitor.record(false);
      callLogger.logCall(Scene.PROFILE_EXTRACT, null, request.caller(), summary, elapsed(start), false, ex.getErrorCode() + " " + ex.getMessage());
      return ProfileUpdates.empty();
    } catch (RuntimeException ex) {
      circuitBreaker.recordFailure();
      healthMonitor.record(false);
      callLogger.logCall(Scene.PROFILE_EXTRACT, null, request.caller(), summary, elapsed(start), false, ex.getMessage());
      return ProfileUpdates.empty();
    }
  }

  private long elapsed(long start) {
    return System.currentTimeMillis() - start;
  }
}
