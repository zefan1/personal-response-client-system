package com.privateflow.modules.image.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.image.ImageErrorCodes;
import com.privateflow.modules.image.ImageRecognitionException;
import com.privateflow.modules.image.config.ImageConfig;
import com.privateflow.modules.image.config.ImageConfigProvider;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mock-externals", havingValue = "false", matchIfMissing = true)
public class HttpImageRecognitionClient implements ImageRecognitionClient, ConfigurableImageRecognitionClient {

  private static final Logger log = LoggerFactory.getLogger(HttpImageRecognitionClient.class);
  private static final String DEFAULT_MODEL = "qwen3-vl-plus";
  private final HttpClient httpClient;
  private final ImageConfigProvider configProvider;
  private final ObjectMapper objectMapper;

  public HttpImageRecognitionClient(ImageConfigProvider configProvider, ObjectMapper objectMapper) {
    this.configProvider = configProvider;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .version(HttpClient.Version.HTTP_1_1)
        .build();
  }

  @Override
  public String recognize(byte[] jpegImage) {
    ImageConfig config = configProvider.get();
    return recognize(jpegImage, config);
  }

  public String recognize(byte[] jpegImage, ImageConfig config) {
    if (config.apiBaseUrl() == null || config.apiBaseUrl().isBlank()) {
      throw failed("Image recognition API base URL is not configured");
    }
    byte[] body = requestBody(config, jpegImage);
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(chatCompletionsUrl(config.apiBaseUrl())))
        .timeout(Duration.ofMillis(config.timeoutMs()))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofByteArray(body));
    if (config.apiKey() != null && !config.apiKey().isBlank()) {
      builder.header("Authorization", "Bearer " + config.apiKey());
    }
    try {
      HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      int status = response.statusCode();
      if (status == 401 || status == 403) {
        log.error("IMAGE_API_AUTH_FAILED status={}", status);
        throw failed("Image recognition API key is invalid");
      }
      if (status == 429) {
        throw failed("Image recognition service is rate limited");
      }
      if (status >= 500) {
        throw failed("Image recognition service returned server error");
      }
      if (status < 200 || status >= 300) {
        log.warn("IMAGE_API_HTTP_FAILED status={} body={}", status, response.body());
        throw failed("Image recognition service request failed");
      }
      if (response.body() == null || response.body().isBlank()) {
        throw failed("Image recognition service returned empty response");
      }
      return unwrapProviderResponse(response.body());
    } catch (java.net.http.HttpTimeoutException ex) {
      throw new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, "Image recognition timed out", ex);
    } catch (IOException ex) {
      throw new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, "Image recognition service is unreachable", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, "Image recognition request was interrupted", ex);
    }
  }

  private byte[] requestBody(ImageConfig config, byte[] image) {
    try {
      Map<String, Object> payload = Map.of(
          "model", model(config),
          "messages", List.of(
              Map.of("role", "system", "content", config.recognitionPrompt() == null ? "" : config.recognitionPrompt()),
              Map.of(
                  "role", "user",
                  "content", List.of(
                      Map.of("type", "text", "text", "Return only valid JSON for nickname, phone, messages and timestamp."),
                      Map.of("type", "image_url", "image_url", Map.of("url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image)))))),
          "stream", false);
      return objectMapper.writeValueAsBytes(payload);
    } catch (IOException ex) {
      throw new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, "Image recognition request could not be serialized", ex);
    }
  }

  private String chatCompletionsUrl(String baseUrl) {
    String normalized = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    if (normalized.endsWith("/chat/completions")) {
      return normalized;
    }
    if (normalized.endsWith("/v1")) {
      return normalized + "/chat/completions";
    }
    return normalized + "/v1/chat/completions";
  }

  private String model(ImageConfig config) {
    return config.model() == null || config.model().isBlank() ? DEFAULT_MODEL : config.model().trim();
  }

  private String unwrapProviderResponse(String raw) {
    try {
      JsonNode root = objectMapper.readTree(raw);
      JsonNode content = root.path("choices").path(0).path("message").path("content");
      if (content.isTextual() && !content.asText().isBlank()) {
        return content.asText();
      }
      return raw;
    } catch (Exception ignored) {
      return raw;
    }
  }

  private ImageRecognitionException failed(String message) {
    return new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, message);
  }
}
