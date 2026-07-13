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
public class LlmSummaryService {

  private static final Logger log = LoggerFactory.getLogger(LlmSummaryService.class);
  private static final String ENABLED_KEY = "llm.summary.enabled";
  private static final String SYSTEM_PROMPT_KEY = "llm.summary.system_prompt";
  private static final String TEMPERATURE_KEY = "llm.summary.temperature";
  private static final String MAX_TOKENS_KEY = "llm.summary.max_tokens";
  private static final String DEFAULT_SYSTEM_PROMPT = """
      You summarize a private-domain postpartum recovery sales conversation for CRM follow-up notes.
      Return JSON only. Schema: {"summary":"one concise Chinese follow-up note, within 120 Chinese characters"}.
      Include customer intent, key concern, and agreed next step if present.
      Do not include full phone numbers or unsupported medical diagnosis.
      """;

  private final LlmService llmService;
  private final SystemConfigRepository configRepository;
  private final ObjectMapper objectMapper;

  public LlmSummaryService(
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

  public Optional<String> trySummarize(LlmSummaryInput input) {
    if (!enabled()) {
      return Optional.empty();
    }
    LlmResponse response = llmService.generate(
        LlmScene.SUMMARY,
        input == null ? "" : input.leadType(),
        input == null ? "" : input.caller(),
        requestSummary(input),
        buildRequest(input));
    if (!response.success()) {
      return Optional.empty();
    }
    return parse(cleanJson(response.content()));
  }

  private LlmRequest buildRequest(LlmSummaryInput input) {
    return new LlmRequest(
        systemPrompt(),
        userPrompt(input),
        List.of(),
        decimalConfig(TEMPERATURE_KEY),
        integerConfig(MAX_TOKENS_KEY));
  }

  private String userPrompt(LlmSummaryInput input) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("leadType", input == null ? "" : nvl(input.leadType()));
    payload.put("nickname", input == null ? "" : nvl(input.nickname()));
    payload.put("sentText", input == null ? "" : clip(input.sentText(), 500));
    payload.put("selectedDirection", input == null ? "" : nvl(input.selectedDirection()));
    payload.put("rawMessages", sanitizeMessages(input == null ? List.of() : input.rawMessages()));
    String phone = input == null ? "" : input.phone();
    if (phone != null && phone.length() >= 4) {
      payload.put("phoneLast4", phone.substring(phone.length() - 4));
    }
    return """
        Summarize this confirmed customer conversation for follow-up notes.
        Input JSON:
        %s
        """.formatted(toJson(payload));
  }

  private List<Map<String, String>> sanitizeMessages(List<CustomerMessageSentEvent.ChatMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return List.of();
    }
    return messages.stream()
        .skip(Math.max(0, messages.size() - 10))
        .map(message -> Map.of(
            "role", nvl(message.role()),
            "text", clip(nvl(message.text()), 400)))
        .toList();
  }

  private Optional<String> parse(String raw) {
    try {
      JsonNode node = objectMapper.readTree(raw);
      String summary = text(node.path("summary"));
      return summary == null || summary.isBlank() ? Optional.empty() : Optional.of(clip(summary, 160));
    } catch (Exception ex) {
      log.warn("LLM summary parse failed: {}", ex.getMessage());
      return Optional.empty();
    }
  }

  private String systemPrompt() {
    return configRepository.findValue(SYSTEM_PROMPT_KEY)
        .filter(value -> !value.isBlank())
        .orElse(DEFAULT_SYSTEM_PROMPT);
  }

  private String requestSummary(LlmSummaryInput input) {
    String text = input == null ? "" : nvl(input.sentText()).replaceAll("\\d{11}", "[PHONE]");
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
