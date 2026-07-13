package com.privateflow.modules.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LlmAbnormalDetectionService {

  private static final Logger log = LoggerFactory.getLogger(LlmAbnormalDetectionService.class);
  private static final String ENABLED_KEY = "llm.abnormal_detection.enabled";
  private static final String SYSTEM_PROMPT_KEY = "llm.abnormal_detection.system_prompt";
  private static final String TEMPERATURE_KEY = "llm.abnormal_detection.temperature";
  private static final String MAX_TOKENS_KEY = "llm.abnormal_detection.max_tokens";
  private static final String DEFAULT_SYSTEM_PROMPT = """
      You detect customer complaint or churn risk in a private-domain postpartum recovery sales conversation.
      Return JSON only. Schema:
      {"abnormal_alert":{"triggered":true|false,"alert_type":"CUSTOMER_COMPLAINT|CHURN_RISK","level":"ERROR|WARN|INFO","message":"short actionable alert"}}
      Trigger only when there is clear complaint, refund/escalation threat, strong dissatisfaction, loss/churn signal, or explicit refusal.
      Do not trigger for ordinary questions or mild hesitation.
      """;

  private final LlmService llmService;
  private final SystemConfigRepository configRepository;
  private final ObjectMapper objectMapper;

  public LlmAbnormalDetectionService(
      LlmService llmService,
      SystemConfigRepository configRepository,
      ObjectMapper objectMapper) {
    this.llmService = llmService;
    this.configRepository = configRepository;
    this.objectMapper = objectMapper;
  }

  public boolean enabled() {
    return booleanConfig(ENABLED_KEY, false);
  }

  public Optional<LlmAbnormalAlert> tryDetect(LlmAbnormalDetectionInput input) {
    if (!enabled()) {
      return Optional.empty();
    }
    LlmResponse response = llmService.generate(
        LlmScene.ABNORMAL_DETECTION,
        input == null ? "" : input.leadType(),
        input == null ? "" : input.caller(),
        requestSummary(input),
        buildRequest(input));
    if (!response.success()) {
      return Optional.empty();
    }
    return parse(cleanJson(response.content()));
  }

  private LlmRequest buildRequest(LlmAbnormalDetectionInput input) {
    return new LlmRequest(
        systemPrompt(),
        userPrompt(input),
        List.of(),
        decimalConfig(TEMPERATURE_KEY),
        integerConfig(MAX_TOKENS_KEY));
  }

  private String userPrompt(LlmAbnormalDetectionInput input) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("leadType", input == null ? "" : nvl(input.leadType()));
    payload.put("nickname", input == null ? "" : nvl(input.nickname()));
    payload.put("conversationSummary", input == null ? "" : clip(input.conversationSummary(), 1200));
    payload.put("sentText", input == null ? "" : clip(input.sentText(), 500));
    payload.put("rawMessages", sanitizeMessages(input == null ? List.of() : input.rawMessages()));
    String phone = input == null ? "" : input.phone();
    if (phone != null && phone.length() >= 4) {
      payload.put("phoneLast4", phone.substring(phone.length() - 4));
    }
    return """
        Detect whether this confirmed conversation contains an abnormal alert.
        Input JSON:
        %s
        """.formatted(toJson(payload));
  }

  private List<Map<String, String>> sanitizeMessages(List<CustomerMessageSentEvent.ChatMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return List.of();
    }
    return messages.stream()
        .skip(Math.max(0, messages.size() - 8))
        .map(message -> Map.of(
            "role", nvl(message.role()),
            "text", clip(nvl(message.text()), 400)))
        .toList();
  }

  private Optional<LlmAbnormalAlert> parse(String raw) {
    try {
      JsonNode node = objectMapper.readTree(raw).path("abnormal_alert");
      if (!node.path("triggered").asBoolean(false)) {
        return Optional.empty();
      }
      String alertType = text(node.path("alert_type"));
      String level = text(node.path("level"));
      String message = text(node.path("message"));
      if (!validAlertType(alertType) || !validLevel(level) || message == null || message.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(new LlmAbnormalAlert(alertType, level, clip(message, 160)));
    } catch (Exception ex) {
      log.warn("LLM abnormal detection parse failed: {}", ex.getMessage());
      return Optional.empty();
    }
  }

  private boolean validAlertType(String value) {
    return "CUSTOMER_COMPLAINT".equals(value) || "CHURN_RISK".equals(value);
  }

  private boolean validLevel(String value) {
    return "ERROR".equals(value) || "WARN".equals(value) || "INFO".equals(value);
  }

  private String systemPrompt() {
    return configRepository.findValue(SYSTEM_PROMPT_KEY)
        .filter(value -> !value.isBlank())
        .orElse(DEFAULT_SYSTEM_PROMPT);
  }

  private String requestSummary(LlmAbnormalDetectionInput input) {
    String text = input == null ? "" : nvl(input.conversationSummary()).replaceAll("\\d{11}", "[PHONE]");
    String summary = clip(text, 120);
    String phone = input == null ? "" : input.phone();
    if (phone != null && phone.length() >= 4) {
      summary += "...[phone:" + phone.substring(phone.length() - 4) + "]";
    }
    return clip(summary, 180);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      return "{}";
    }
  }

  private String cleanJson(String content) {
    if (content == null) {
      return "";
    }
    String trimmed = content.trim();
    if (!trimmed.startsWith("```")) {
      return trimmed;
    }
    String withoutOpening = trimmed.replaceFirst("^```[A-Za-z0-9_-]*\\s*", "");
    return withoutOpening.replaceFirst("\\s*```$", "").trim();
  }

  private String text(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    String value = node.asText();
    return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? null : value.trim();
  }

  private boolean booleanConfig(String key, boolean fallback) {
    return configRepository.findValue(key)
        .map(value -> "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()))
        .orElse(fallback);
  }

  private Double decimalConfig(String key) {
    return configRepository.findValue(key)
        .filter(value -> !value.isBlank())
        .map(value -> {
          try {
            return Double.parseDouble(value.trim());
          } catch (NumberFormatException ex) {
            return null;
          }
        })
        .orElse(null);
  }

  private Integer integerConfig(String key) {
    return configRepository.findValue(key)
        .filter(value -> !value.isBlank())
        .map(value -> {
          try {
            return Integer.parseInt(value.trim());
          } catch (NumberFormatException ex) {
            return null;
          }
        })
        .orElse(null);
  }

  private String clip(String value, int maxLength) {
    if (value == null) {
      return "";
    }
    return value.substring(0, Math.min(value.length(), maxLength));
  }

  private String nvl(String value) {
    return value == null ? "" : value;
  }
}
