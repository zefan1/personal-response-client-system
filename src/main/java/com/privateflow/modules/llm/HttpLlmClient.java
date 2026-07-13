package com.privateflow.modules.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HttpLlmClient implements LlmClient {

  private final LlmConfigProvider configProvider;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public HttpLlmClient(LlmConfigProvider configProvider, ObjectMapper objectMapper) {
    this.configProvider = configProvider;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .version(HttpClient.Version.HTTP_1_1)
        .build();
  }

  @Override
  public LlmResponse generate(LlmRequest request) {
    return generate(request, configProvider.get());
  }

  @Override
  public LlmResponse generate(LlmRequest request, LlmConfig config) {
    long start = System.currentTimeMillis();
    try {
      validate(config);
      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(chatCompletionsUrl(config.apiBaseUrl())))
          .timeout(Duration.ofMillis(config.timeoutMs()))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + config.apiKey())
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload(request, config)), StandardCharsets.UTF_8))
          .build();
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return handleResponse(response, config, elapsed(start));
    } catch (java.net.http.HttpTimeoutException ex) {
      return LlmResponse.failed(LlmErrorCodes.TIMEOUT, "LLM 请求超时", safeModel(config), safeProtocol(config), elapsed(start));
    } catch (LlmException ex) {
      return LlmResponse.failed(ex.errorCode(), ex.getMessage(), safeModel(config), safeProtocol(config), elapsed(start));
    } catch (IOException ex) {
      return LlmResponse.failed(LlmErrorCodes.UNREACHABLE, "LLM 服务无法连接", safeModel(config), safeProtocol(config), elapsed(start));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return LlmResponse.failed(LlmErrorCodes.UNREACHABLE, "LLM 请求被中断", safeModel(config), safeProtocol(config), elapsed(start));
    }
  }

  private Map<String, Object> payload(LlmRequest request, LlmConfig config) {
    return Map.of(
        "model", config.model(),
        "messages", messages(request),
        "temperature", request.temperature() == null ? config.temperature() : request.temperature(),
        "max_tokens", request.maxTokens() == null ? config.maxTokens() : request.maxTokens(),
        "stream", false);
  }

  private List<Map<String, String>> messages(LlmRequest request) {
    List<Map<String, String>> messages = new ArrayList<>();
    if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
      messages.add(Map.of("role", "system", "content", request.systemPrompt()));
    }
    if (request.messages() != null) {
      for (LlmMessage message : request.messages()) {
        if (message != null && message.content() != null && !message.content().isBlank()) {
          messages.add(Map.of("role", role(message.role()), "content", message.content()));
        }
      }
    }
    if (request.userPrompt() != null && !request.userPrompt().isBlank()) {
      messages.add(Map.of("role", "user", "content", request.userPrompt()));
    }
    if (messages.isEmpty()) {
      throw new LlmException(LlmErrorCodes.RESPONSE_INVALID, "LLM 请求内容不能为空");
    }
    return messages;
  }

  private LlmResponse handleResponse(HttpResponse<String> response, LlmConfig config, long elapsedMs) {
    int status = response.statusCode();
    if (status == 401 || status == 403) {
      return LlmResponse.failed(LlmErrorCodes.AUTH_FAILED, "LLM API Key 无效", config.model(), config.protocol(), elapsedMs);
    }
    if (status == 429) {
      return LlmResponse.failed(LlmErrorCodes.RATE_LIMITED, "LLM 服务触发限流", config.model(), config.protocol(), elapsedMs);
    }
    if (status < 200 || status >= 300) {
      return LlmResponse.failed(LlmErrorCodes.UNREACHABLE, "LLM 服务请求失败：HTTP " + status, config.model(), config.protocol(), elapsedMs);
    }
    if (response.body() == null || response.body().isBlank()) {
      return LlmResponse.failed(LlmErrorCodes.RESPONSE_INVALID, "LLM 服务返回空内容", config.model(), config.protocol(), elapsedMs);
    }
    String content = content(response.body());
    if (content.isBlank()) {
      return LlmResponse.failed(LlmErrorCodes.RESPONSE_INVALID, "LLM 服务响应缺少正文内容", config.model(), config.protocol(), elapsedMs);
    }
    return LlmResponse.ok(content, config.model(), config.protocol(), elapsedMs);
  }

  private void validate(LlmConfig config) {
    if (config == null || config.apiBaseUrl() == null || config.apiBaseUrl().isBlank()) {
      throw new LlmException(LlmErrorCodes.CONFIG_MISSING, "LLM 服务地址未配置");
    }
    if (config.apiKey() == null || config.apiKey().isBlank()) {
      throw new LlmException(LlmErrorCodes.CONFIG_MISSING, "LLM API Key 未配置");
    }
    if (config.model() == null || config.model().isBlank()) {
      throw new LlmException(LlmErrorCodes.CONFIG_MISSING, "LLM 模型名称未配置");
    }
    if (!LlmConfigProvider.OPENAI_COMPATIBLE.equals(protocol(config.protocol()))) {
      throw new LlmException(LlmErrorCodes.CONFIG_MISSING, "暂不支持该 LLM 协议");
    }
  }

  private String content(String raw) {
    try {
      JsonNode root = objectMapper.readTree(raw);
      JsonNode content = root.path("choices").path(0).path("message").path("content");
      return content.isTextual() ? content.asText() : "";
    } catch (Exception ex) {
      return "";
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

  private String role(String role) {
    String normalized = role == null || role.isBlank() ? "user" : role.trim().toLowerCase();
    return switch (normalized) {
      case "system", "assistant", "user" -> normalized;
      default -> "user";
    };
  }

  private String protocol(String value) {
    return value == null || value.isBlank() ? LlmConfigProvider.OPENAI_COMPATIBLE : value.trim().toUpperCase();
  }

  private String safeModel(LlmConfig config) {
    return config == null || config.model() == null ? "" : config.model();
  }

  private String safeProtocol(LlmConfig config) {
    return config == null ? LlmConfigProvider.OPENAI_COMPATIBLE : protocol(config.protocol());
  }

  private long elapsed(long start) {
    return System.currentTimeMillis() - start;
  }
}
