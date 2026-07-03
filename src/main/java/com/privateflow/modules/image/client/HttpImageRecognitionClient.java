package com.privateflow.modules.image.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.image.ImageErrorCodes;
import com.privateflow.modules.image.ImageRecognitionException;
import com.privateflow.modules.image.config.ImageConfig;
import com.privateflow.modules.image.config.ImageConfigProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mock-externals", havingValue = "false", matchIfMissing = true)
public class HttpImageRecognitionClient implements ImageRecognitionClient {

  private static final Logger log = LoggerFactory.getLogger(HttpImageRecognitionClient.class);
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
    if (config.apiBaseUrl() == null || config.apiBaseUrl().isBlank()) {
      throw new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, "图片识别服务连接异常，请稍后重试或手动复制文字");
    }
    String boundary = "----PrivateFlowImage" + UUID.randomUUID();
    byte[] body = multipartBody(boundary, jpegImage, config.recognitionPrompt());
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(config.apiBaseUrl().replaceAll("/+$", "") + "/v1/chat/completions"))
        .timeout(Duration.ofMillis(config.timeoutMs()))
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .POST(HttpRequest.BodyPublishers.ofByteArray(body));
    if (config.apiKey() != null && !config.apiKey().isBlank()) {
      builder.header("Authorization", "Bearer " + config.apiKey());
    }
    try {
      HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      int status = response.statusCode();
      if (status == 401 || status == 403) {
        log.error("IMAGE_API_AUTH_FAILED status={}", status);
        throw new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, "图片识别服务连接异常，请稍后重试或手动复制文字");
      }
      if (status == 429) {
        throw new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, "图片识别服务繁忙，请稍后重试");
      }
      if (status >= 500) {
        throw new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, "图片识别服务内部错误，请稍后重试");
      }
      if (status < 200 || status >= 300) {
        throw new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, "图片识别服务连接异常，请稍后重试或手动复制文字");
      }
      if (response.body() == null || response.body().isBlank()) {
        throw new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, "图片识别服务返回空响应");
      }
      return unwrapProviderResponse(response.body());
    } catch (java.net.http.HttpTimeoutException ex) {
      throw new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, "图片识别超时，建议重新截图或手动复制文字", ex);
    } catch (IOException ex) {
      throw new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, "图片识别服务连接异常，请稍后重试或手动复制文字", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, "图片识别服务连接异常，请稍后重试或手动复制文字", ex);
    }
  }

  private byte[] multipartBody(String boundary, byte[] image, String prompt) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      write(out, "--" + boundary + "\r\n");
      write(out, "Content-Disposition: form-data; name=\"system_prompt\"\r\n\r\n");
      write(out, prompt == null ? "" : prompt);
      write(out, "\r\n--" + boundary + "\r\n");
      write(out, "Content-Disposition: form-data; name=\"image\"; filename=\"screenshot.jpg\"\r\n");
      write(out, "Content-Type: image/jpeg\r\n\r\n");
      out.write(image);
      write(out, "\r\n--" + boundary + "--\r\n");
      return out.toByteArray();
    } catch (IOException ex) {
      throw new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, "图片识别服务连接异常，请稍后重试或手动复制文字", ex);
    }
  }

  private void write(ByteArrayOutputStream out, String value) throws IOException {
    out.write(value.getBytes(StandardCharsets.UTF_8));
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
}
