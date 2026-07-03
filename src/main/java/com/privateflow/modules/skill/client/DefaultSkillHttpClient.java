package com.privateflow.modules.skill.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.skill.SkillErrorCodes;
import com.privateflow.modules.skill.SkillGatewayException;
import com.privateflow.modules.skill.config.SkillConfig;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mock-externals", havingValue = "false", matchIfMissing = true)
public class DefaultSkillHttpClient implements SkillHttpClient {

  private static final Logger log = LoggerFactory.getLogger(DefaultSkillHttpClient.class);
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final SkillConfigProvider configProvider;

  public DefaultSkillHttpClient(ObjectMapper objectMapper, SkillConfigProvider configProvider) {
    this.objectMapper = objectMapper;
    this.configProvider = configProvider;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .version(HttpClient.Version.HTTP_1_1)
        .build();
  }

  @Override
  public String call(Map<String, Object> payload, int timeoutMs) {
    SkillConfig config = configProvider.get();
    if (config.apiBaseUrl() == null || config.apiBaseUrl().isBlank()) {
      throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE, "Skill 系统不可达：API 地址未配置", false);
    }
    try {
      String body = objectMapper.writeValueAsString(payload);
      HttpRequest.Builder builder = HttpRequest.newBuilder()
          .uri(URI.create(config.apiBaseUrl().replaceAll("/+$", "") + "/v1/chat/completions"))
          .timeout(Duration.ofMillis(timeoutMs))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
      if (config.apiKey() != null && !config.apiKey().isBlank()) {
        builder.header("Authorization", "Bearer " + config.apiKey());
      }
      HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      int status = response.statusCode();
      if (status == 401 || status == 403) {
        log.error("SKILL_API_AUTH_FAILED status={}", status);
        throw new SkillGatewayException(SkillErrorCodes.SKILL_API_KEY_INVALID, "Skill API key 无效", true);
      }
      if (status == 429) {
        throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE, "Skill 系统限流", false);
      }
      if (status >= 500) {
        throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE, "Skill 系统服务异常", true);
      }
      if (status < 200 || status >= 300) {
        throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE, "Skill 系统不可达", false);
      }
      if (response.body() == null || response.body().isBlank()) {
        throw new SkillGatewayException(SkillErrorCodes.SKILL_RESPONSE_INVALID, "Skill 返回空响应", true);
      }
      return unwrapProviderResponse(response.body());
    } catch (java.net.http.HttpTimeoutException ex) {
      throw new SkillGatewayException(SkillErrorCodes.SKILL_TIMEOUT, "Skill 调用超时", true, ex);
    } catch (IOException ex) {
      throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE, "Skill 系统不可达", false, ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE, "Skill 系统不可达", false, ex);
    }
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
