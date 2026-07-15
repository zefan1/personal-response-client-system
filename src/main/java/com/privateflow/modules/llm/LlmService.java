package com.privateflow.modules.llm;

import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class LlmService {

  private final LlmClient client;
  private final LlmRoutingService routingService;
  private final LlmCallLogger callLogger;

  public LlmService(LlmClient client, LlmRoutingService routingService, LlmCallLogger callLogger) {
    this.client = client;
    this.routingService = routingService;
    this.callLogger = callLogger;
  }

  public LlmResponse generate(LlmRequest request) {
    return client.generate(request);
  }

  public LlmResponse generate(LlmScene scene, String leadType, String caller, String requestSummary, LlmRequest request) {
    LlmResponse lastResponse = null;
    for (LlmRouteResolution resolution : routingService.resolveCandidates(scene, leadType)) {
      LlmResponse response = client.generate(request, resolution.config());
      callLogger.logCall(
          scene,
          resolution.leadType(),
          caller,
          resolution.routeId(),
          resolution.environmentId(),
          response.model(),
          response.protocol(),
          requestSummary,
          response.elapsedMs(),
          response.success(),
          response.errorCode(),
          response.message());
      if (response.success()) {
        return response;
      }
      lastResponse = response;
    }
    if (lastResponse != null) {
      return lastResponse;
    }
    return LlmResponse.failed(LlmErrorCodes.CONFIG_MISSING, "LLM 路由未配置", "", LlmConfigProvider.OPENAI_COMPATIBLE, 0);
  }

  public <T> Optional<T> generateValidated(
      LlmScene scene,
      String leadType,
      String caller,
      String requestSummary,
      LlmRequest request,
      Function<String, T> responseValidator) {
    for (LlmRouteResolution resolution : routingService.resolveCandidates(scene, leadType)) {
      LlmResponse response = client.generate(request, resolution.config());
      if (!response.success()) {
        logCall(scene, caller, requestSummary, resolution, response, false, response.errorCode(), response.message());
        continue;
      }
      try {
        T validated = responseValidator.apply(response.content());
        if (validated == null) {
          throw new IllegalArgumentException("LLM response validation returned no result");
        }
        logCall(scene, caller, requestSummary, resolution, response, true, null, null);
        return Optional.of(validated);
      } catch (RuntimeException ex) {
        logCall(
            scene,
            caller,
            requestSummary,
            resolution,
            response,
            false,
            LlmErrorCodes.RESPONSE_INVALID,
            ex.getMessage());
      }
    }
    return Optional.empty();
  }

  private void logCall(
      LlmScene scene,
      String caller,
      String requestSummary,
      LlmRouteResolution resolution,
      LlmResponse response,
      boolean success,
      String errorCode,
      String errorMessage) {
    callLogger.logCall(
        scene,
        resolution.leadType(),
        caller,
        resolution.routeId(),
        resolution.environmentId(),
        response.model(),
        response.protocol(),
        requestSummary,
        response.elapsedMs(),
        success,
        errorCode,
        errorMessage);
  }

  public LlmResponse test(LlmConfig config) {
    return client.generate(LlmRequest.singleTurn("You are a connectivity test endpoint.", "Reply with OK only."), config);
  }

  public LlmResponse test(LlmRequest request, LlmConfig config) {
    return client.generate(request, config);
  }
}
