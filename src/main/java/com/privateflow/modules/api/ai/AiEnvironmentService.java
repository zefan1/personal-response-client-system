package com.privateflow.modules.api.ai;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.image.ImageRecognitionService;
import com.privateflow.modules.image.RecognitionResult;
import com.privateflow.modules.image.Source;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class AiEnvironmentService {

  private static final byte[] TEST_IMAGE = Base64.getDecoder().decode(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=");
  private static final List<String> ENV_CONFIG_KEYS = List.of("skill.api_base_url", "skill.api_key", "image.api_base_url", "image.api_key");
  private static final String IMAGE_TEST_STATUS_COLUMN = "last_test_ok";
  private final AiEnvironmentRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final WsPushService wsPushService;
  private final ImageRecognitionService imageRecognitionService;

  public AiEnvironmentService(
      AiEnvironmentRepository repository,
      ApplicationEventPublisher eventPublisher,
      WsPushService wsPushService,
      ImageRecognitionService imageRecognitionService) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
    this.imageRecognitionService = imageRecognitionService;
  }

  public List<AiEnvironment> list(AiEnvironmentType type) {
    return repository.list(type);
  }

  public AiEnvironment create(AiEnvironmentType type, AiEnvironmentRequest request) {
    validate(request);
    long id = repository.create(type, request);
    return repository.find(type, id).orElseThrow();
  }

  public AiEnvironment update(AiEnvironmentType type, long id, AiEnvironmentRequest request) {
    repository.find(type, id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "environment not found"));
    validate(request);
    repository.update(type, id, request);
    return repository.find(type, id).orElseThrow();
  }

  public AiEnvironment activate(AiEnvironmentType type, long id) {
    AiEnvironment environment = repository.find(type, id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "environment not found"));
    repository.activate(type, environment);
    String apiKey = AiEnvironmentRepository.decrypt(repository.encryptedApiKey(type, id));
    repository.updateConfig(type.baseUrlConfigKey(), environment.baseUrl());
    repository.updateConfig(type.apiKeyConfigKey(), apiKey);
    publishConfig(type.baseUrlConfigKey());
    publishConfig(type.apiKeyConfigKey());
    return repository.find(type, id).orElseThrow();
  }

  public void delete(AiEnvironmentType type, long id) {
    AiEnvironment environment = repository.find(type, id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "environment not found"));
    if (environment.active()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "active environment cannot be deleted");
    }
    if (repository.count(type) <= 1) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "at least one environment must remain");
    }
    repository.delete(type, id);
  }

  public ImageEnvironmentTestResponse testImage(long id) {
    repository.find(AiEnvironmentType.IMAGE, id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "environment not found"));
    long start = System.currentTimeMillis();
    try {
      RecognitionResult result = imageRecognitionService.recognize(TEST_IMAGE, Source.BUTTON_CLICK);
      long elapsed = System.currentTimeMillis() - start;
      repository.markImageTest(id, true);
      return new ImageEnvironmentTestResponse(true, elapsed, Map.of(
          "nickname", result.nickname() == null ? "" : result.nickname(),
          "messagesCount", result.messages() == null ? 0 : result.messages().size(),
          "firstMessage", result.messages() == null || result.messages().isEmpty() ? "" : result.messages().get(0).text(),
          "hasPhone", result.phone() != null && !result.phone().isBlank()), null, null, null);
    } catch (RuntimeException ex) {
      long elapsed = System.currentTimeMillis() - start;
      repository.markImageTest(id, false);
      return new ImageEnvironmentTestResponse(false, elapsed, null, "30-10001", ex.getMessage(), "请检查 baseUrl、API Key 和网络连通性");
    }
  }

  private void validate(AiEnvironmentRequest request) {
    if (request == null || request.envName() == null || request.envName().isBlank() || request.envName().length() > 50) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "envName is required and max 50 chars");
    }
    if (request.baseUrl() == null || request.baseUrl().isBlank() || request.baseUrl().length() > 500) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "baseUrl is required");
    }
    try {
      URI uri = URI.create(request.baseUrl().trim());
      if (uri.getScheme() == null || uri.getHost() == null) {
        throw new IllegalArgumentException();
      }
    } catch (RuntimeException ex) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "baseUrl must be valid URL");
    }
    if (request.apiKey() == null || request.apiKey().isBlank() || request.apiKey().length() > 500) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "apiKey is required");
    }
  }

  private void publishConfig(String key) {
    eventPublisher.publishEvent(new ConfigChangedEvent(key));
    wsPushService.broadcastWs(WsMessage.unsaved("CONFIG_REFRESH", Map.of("configKey", key, "operator", AuthContext.username())));
  }
}
