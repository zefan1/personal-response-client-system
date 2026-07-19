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
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mock-externals", havingValue = "false", matchIfMissing = true)
public class DefaultSkillHttpClient implements SkillHttpClient {

  private static final Logger log = LoggerFactory.getLogger(DefaultSkillHttpClient.class);
  private static final String MCP_PROTOCOL = "MCP_STREAMABLE_HTTP";
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
      throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE, "Skill API base URL is not configured", false);
    }
    if (MCP_PROTOCOL.equalsIgnoreCase(config.protocol())) {
      return callMcp(payload, timeoutMs, config);
    }
    return callOpenAiCompatible(payload, timeoutMs, config);
  }

  private String callOpenAiCompatible(Map<String, Object> payload, int timeoutMs, SkillConfig config) {
    try {
      String body = objectMapper.writeValueAsString(payload);
      HttpRequest.Builder builder = HttpRequest.newBuilder()
          .uri(URI.create(config.apiBaseUrl().replaceAll("/+$", "") + "/v1/chat/completions"))
          .timeout(Duration.ofMillis(timeoutMs))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
      addAuth(builder, config, payload);
      HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      checkHttpStatus(response.statusCode());
      if (response.body() == null || response.body().isBlank()) {
        throw new SkillGatewayException(SkillErrorCodes.SKILL_RESPONSE_INVALID, "Skill returned an empty response", true);
      }
      return unwrapProviderResponse(response.body());
    } catch (java.net.http.HttpTimeoutException ex) {
      throw new SkillGatewayException(SkillErrorCodes.SKILL_TIMEOUT, "Skill request timed out", true, ex);
    } catch (IOException ex) {
      throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE, "Skill is unreachable", false, ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE, "Skill is unreachable", false, ex);
    }
  }

  private String callMcp(Map<String, Object> payload, int timeoutMs, SkillConfig config) {
    String skillId = stringValue(payload.get("skill_id"));
    if (skillId.isBlank()) {
      skillId = config.defaultSkillId();
    }
    if (skillId == null || skillId.isBlank()) {
      throw new SkillGatewayException(SkillErrorCodes.SKILL_ROUTE_NOT_CONFIGURED, "MCP Skill ID is not configured", false, false);
    }
    try {
      HttpResponse<String> initialize = sendMcp(config.apiBaseUrl(), config.apiKey(), skillId, null,
          Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize", "params", Map.of(
              "protocolVersion", "2025-06-18",
              "capabilities", Map.of(),
              "clientInfo", Map.of("name", "privateflow", "version", "1.0"))), timeoutMs);
      String sessionId = initialize.headers().firstValue("Mcp-Session-Id").orElse("");
      if (sessionId.isBlank()) {
        throw new SkillGatewayException(SkillErrorCodes.SKILL_RESPONSE_INVALID, "MCP session ID is missing", true);
      }
      sendMcp(config.apiBaseUrl(), config.apiKey(), skillId, sessionId,
          Map.of("jsonrpc", "2.0", "method", "notifications/initialized", "params", Map.of()), timeoutMs);

      Map<String, Object> arguments = new LinkedHashMap<>();
      arguments.put("query", buildMcpQuery(payload));
      arguments.put("context", payload);
      HttpResponse<String> toolResponse = sendMcp(config.apiBaseUrl(), config.apiKey(), skillId, sessionId,
          Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/call", "params", Map.of(
              "name", mcpToolName(skillId),
              "arguments", arguments)), timeoutMs);
      JsonNode root = parseSseJson(toolResponse.body());
      JsonNode error = root.path("error");
      if (error.isObject()) {
        throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE,
            error.path("message").asText("MCP tool call failed"), false);
      }
      JsonNode result = root.path("result");
      if (result.path("isError").asBoolean(false)) {
        throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE,
            result.path("content").path(0).path("text").asText("MCP tool call failed"), false);
      }
      String text = result.path("content").path(0).path("text").asText("");
      if (text.isBlank()) {
        throw new SkillGatewayException(SkillErrorCodes.SKILL_RESPONSE_INVALID, "MCP tool returned no text", true);
      }
      return text;
    } catch (java.net.http.HttpTimeoutException ex) {
      throw new SkillGatewayException(SkillErrorCodes.SKILL_TIMEOUT, "Skill request timed out", true, ex);
    } catch (IOException ex) {
      throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE, "Skill is unreachable", false, ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE, "Skill is unreachable", false, ex);
    }
  }

  private HttpResponse<String> sendMcp(
      String url,
      String apiKey,
      String skillId,
      String sessionId,
      Map<String, Object> body,
      int timeoutMs) throws IOException, InterruptedException {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofMillis(timeoutMs))
        .header("Accept", "application/json, text/event-stream")
        .header("Content-Type", "application/json")
        .header("X-Skill-Id", skillId)
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
    if (apiKey != null && !apiKey.isBlank()) {
      builder.header("Authorization", "Bearer " + apiKey);
    }
    if (sessionId != null && !sessionId.isBlank()) {
      builder.header("Mcp-Session-Id", sessionId);
    }
    HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    checkHttpStatus(response.statusCode());
    return response;
  }

  private void addAuth(HttpRequest.Builder builder, SkillConfig config, Map<String, Object> payload) {
    if (config.apiKey() != null && !config.apiKey().isBlank()) {
      builder.header("Authorization", "Bearer " + config.apiKey());
    }
    String skillId = stringValue(payload.get("skill_id"));
    if (!skillId.isBlank()) {
      builder.header("X-Skill-Id", skillId);
    }
  }

  private void checkHttpStatus(int status) {
    if (status == 401 || status == 403) {
      log.error("SKILL_API_AUTH_FAILED status={}", status);
      throw new SkillGatewayException(SkillErrorCodes.SKILL_API_KEY_INVALID, "Skill API key is invalid", true);
    }
    if (status == 429) {
      throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE, "Skill rate limit exceeded", false);
    }
    if (status >= 500) {
      throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE, "Skill service error", true);
    }
    if (status < 200 || status >= 300) {
      throw new SkillGatewayException(SkillErrorCodes.SKILL_UNREACHABLE, "Skill returned HTTP " + status, false);
    }
  }

  private JsonNode parseSseJson(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new SkillGatewayException(SkillErrorCodes.SKILL_RESPONSE_INVALID, "MCP returned an empty response", true);
    }
    for (String line : raw.split("\\R")) {
      if (line.startsWith("data:")) {
        String data = line.substring("data:".length()).trim();
        if (!data.isBlank()) {
          try {
            return objectMapper.readTree(data);
          } catch (IOException ex) {
            throw new SkillGatewayException(SkillErrorCodes.SKILL_RESPONSE_INVALID, "MCP returned invalid JSON", true, ex);
          }
        }
      }
    }
    throw new SkillGatewayException(SkillErrorCodes.SKILL_RESPONSE_INVALID, "MCP returned no message event", true);
  }

  private String buildMcpQuery(Map<String, Object> payload) {
    String clientMessage = stringValue(payload.get("client_message"));
    if (clientMessage.isBlank()) {
      clientMessage = stringValue(payload.get("conversation_text"));
    }
    String systemPrompt = stringValue(payload.get("system_prompt"));
    return "业务场景: " + stringValue(payload.get("scene"))
        + "\n客户消息: " + clientMessage
        + "\n请执行技能的固定思考流程，并严格只返回 JSON 对象。输出字段必须包括 suggestions 数组；每项包含 text、direction、reason。"
        + (systemPrompt.isBlank() ? "" : "\n系统输出要求:\n" + systemPrompt);
  }

  private String mcpToolName(String skillId) {
    String normalized = skillId.trim().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "").toLowerCase();
    return normalized + "__query";
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
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
