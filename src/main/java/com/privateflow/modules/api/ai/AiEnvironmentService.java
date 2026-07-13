package com.privateflow.modules.api.ai;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.image.RecognitionResult;
import com.privateflow.modules.image.client.ConfigurableImageRecognitionClient;
import com.privateflow.modules.image.config.ImageConfig;
import com.privateflow.modules.image.config.ImageConfigProvider;
import com.privateflow.modules.image.parser.RecognitionResultParser;
import com.privateflow.modules.llm.LlmConfig;
import com.privateflow.modules.llm.LlmResponse;
import com.privateflow.modules.llm.LlmService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiEnvironmentService {

  private static final byte[] TEST_IMAGE = createTestImage();
  private static final String DEFAULT_LLM_PROTOCOL = "OPENAI_COMPATIBLE";
  private final AiEnvironmentRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final WsPushService wsPushService;
  private final ImageConfigProvider imageConfigProvider;
  private final ObjectProvider<ConfigurableImageRecognitionClient> imageTestClientProvider;
  private final RecognitionResultParser recognitionResultParser;
  private final LlmService llmService;

  public AiEnvironmentService(
      AiEnvironmentRepository repository,
      ApplicationEventPublisher eventPublisher,
      WsPushService wsPushService,
      ImageConfigProvider imageConfigProvider,
      ObjectProvider<ConfigurableImageRecognitionClient> imageTestClientProvider,
      RecognitionResultParser recognitionResultParser,
      LlmService llmService) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
    this.imageConfigProvider = imageConfigProvider;
    this.imageTestClientProvider = imageTestClientProvider;
    this.recognitionResultParser = recognitionResultParser;
    this.llmService = llmService;
  }

  public List<AiEnvironment> list(AiEnvironmentType type) {
    return repository.list(type);
  }

  public AiEnvironment create(AiEnvironmentType type, AiEnvironmentRequest request) {
    validate(type, request, true);
    long id = repository.create(type, request);
    return repository.find(type, id).orElseThrow();
  }

  @Transactional
  public AiEnvironment update(AiEnvironmentType type, long id, AiEnvironmentRequest request) {
    repository.find(type, id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "environment not found"));
    validate(type, request, false);
    repository.update(type, id, request);
    AiEnvironment updated = repository.find(type, id).orElseThrow();
    if (updated.active()) {
      syncRuntimeConfig(type, updated);
    }
    return updated;
  }

  @Transactional
  public AiEnvironment activate(AiEnvironmentType type, long id) {
    AiEnvironment environment = repository.find(type, id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "environment not found"));
    repository.activate(type, environment);
    syncRuntimeConfig(type, environment);
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
    AiEnvironment environment = repository.find(AiEnvironmentType.IMAGE, id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "environment not found"));
    ConfigurableImageRecognitionClient testClient = imageTestClientProvider.getIfAvailable();
    if (testClient == null) {
      repository.markImageTest(id, false);
      return new ImageEnvironmentTestResponse(
          false,
          0L,
          null,
          ApiErrorCodes.CONFIG_INVALID,
          "当前为本地模拟模式，无法验证真实识图环境",
          "切换到真实接口模式后再测试该环境");
    }
    long start = System.currentTimeMillis();
    try {
      ImageConfig active = imageConfigProvider.get();
      ImageConfig testConfig = new ImageConfig(
          environment.baseUrl(),
          repository.decryptApiKey(AiEnvironmentType.IMAGE, id),
          active.timeoutMs(),
          active.maxSizeBytes(),
          active.maxDimensionPx(),
          active.compressQuality(),
          active.recognitionPrompt(),
          active.model(),
          active.consecutiveFailuresAlert());
      RecognitionResult result = recognitionResultParser.parse(testClient.recognize(TEST_IMAGE, testConfig));
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

  public ImageEnvironmentTestResponse testLlm(long id) {
    AiEnvironment environment = repository.find(AiEnvironmentType.LLM, id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "environment not found"));
    LlmResponse response = llmService.test(new LlmConfig(
        environment.baseUrl(),
        repository.decryptApiKey(AiEnvironmentType.LLM, id),
        environment.model(),
        protocol(environment.protocol()),
        environment.timeoutMs() == null ? 10000 : environment.timeoutMs(),
        environment.temperature() == null ? 0.2 : environment.temperature(),
        environment.maxTokens() == null ? 1024 : environment.maxTokens()));
    if (response.success()) {
      repository.markLlmTest(id, true);
      return new ImageEnvironmentTestResponse(true, response.elapsedMs(), Map.of(
          "model", response.model(),
          "protocol", response.protocol(),
          "content", response.content()), null, null, null);
    }
    repository.markLlmTest(id, false);
    return new ImageEnvironmentTestResponse(false, response.elapsedMs(), null, response.errorCode(), response.message(), "请检查 baseUrl、API Key、模型名和网络连通性");
  }

  private void validate(AiEnvironmentType type, AiEnvironmentRequest request, boolean requireApiKey) {
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
    if (requireApiKey && (request.apiKey() == null || request.apiKey().isBlank())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "apiKey is required");
    }
    if (request.apiKey() != null && !request.apiKey().isBlank() && request.apiKey().length() > 500) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "apiKey max 500 chars");
    }
    if (type == AiEnvironmentType.LLM) {
      validateLlm(request);
    }
  }

  private void validateLlm(AiEnvironmentRequest request) {
    if (request.model() == null || request.model().isBlank() || request.model().length() > 100) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "model is required and max 100 chars");
    }
    String protocol = protocol(request.protocol());
    if (!DEFAULT_LLM_PROTOCOL.equals(protocol)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "protocol must be OPENAI_COMPATIBLE");
    }
    if (request.timeoutMs() == null || request.timeoutMs() < 1000 || request.timeoutMs() > 60000) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "timeoutMs range is 1000-60000");
    }
    if (request.temperature() == null || request.temperature() < 0 || request.temperature() > 2) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "temperature range is 0-2");
    }
    if (request.maxTokens() == null || request.maxTokens() < 1 || request.maxTokens() > 32000) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "maxTokens range is 1-32000");
    }
  }

  private void publishConfig(String key) {
    eventPublisher.publishEvent(new ConfigChangedEvent(key));
    wsPushService.broadcastWs(WsMessage.unsaved("CONFIG_REFRESH", Map.of("configKey", key, "operator", AuthContext.username())));
  }

  private void syncRuntimeConfig(AiEnvironmentType type, AiEnvironment environment) {
    repository.updateConfig(type.baseUrlConfigKey(), environment.baseUrl());
    repository.updateConfig(type.apiKeyConfigKey(), repository.encryptedApiKey(type, environment.id()));
    publishConfig(type.baseUrlConfigKey());
    publishConfig(type.apiKeyConfigKey());
    if (type == AiEnvironmentType.LLM) {
      updateLlmConfig("llm.model", environment.model());
      updateLlmConfig("llm.protocol", protocol(environment.protocol()));
      updateLlmConfig("llm.timeout_ms", String.valueOf(environment.timeoutMs()));
      updateLlmConfig("llm.temperature", String.valueOf(environment.temperature()));
      updateLlmConfig("llm.max_tokens", String.valueOf(environment.maxTokens()));
    }
  }

  private void updateLlmConfig(String key, String value) {
    repository.updateConfig(key, value == null ? "" : value);
    publishConfig(key);
  }

  private String protocol(String value) {
    return value == null || value.isBlank() ? DEFAULT_LLM_PROTOCOL : value.trim().toUpperCase();
  }

  private static byte[] createTestImage() {
    try {
      BufferedImage image = new BufferedImage(420, 260, BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = image.createGraphics();
      try {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(245, 245, 245));
        graphics.fillRect(0, 0, 420, 260);
        graphics.setColor(new Color(237, 237, 237));
        graphics.fillRect(0, 0, 420, 42);
        graphics.setColor(Color.BLACK);
        graphics.drawString("WeChat Chat - Li", 14, 26);
        drawBubble(graphics, 18, 62, 260, 40, Color.WHITE, "Client: Hi, postpartum recovery?");
        drawBubble(graphics, 122, 124, 276, 40, new Color(220, 248, 198), "Keeper: Sure, how many months?");
        drawBubble(graphics, 18, 186, 297, 40, Color.WHITE, "Client: Phone 13800000001");
      } finally {
        graphics.dispose();
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      ImageIO.write(image, "jpg", output);
      return output.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException("could not create image environment test fixture", ex);
    }
  }

  private static void drawBubble(Graphics2D graphics, int x, int y, int width, int height, Color color, String text) {
    graphics.setColor(color);
    graphics.fillRoundRect(x, y, width, height, 10, 10);
    graphics.setColor(Color.BLACK);
    graphics.drawString(text, x + 12, y + 25);
  }
}
