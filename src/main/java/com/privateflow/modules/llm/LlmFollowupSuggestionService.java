package com.privateflow.modules.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LlmFollowupSuggestionService {

  private static final Logger log = LoggerFactory.getLogger(LlmFollowupSuggestionService.class);
  private static final String ENABLED_KEY = "llm.followup_suggestion.enabled";
  private static final String SYSTEM_PROMPT_KEY = "llm.followup_suggestion.system_prompt";
  private static final String TEMPERATURE_KEY = "llm.followup_suggestion.temperature";
  private static final String MAX_TOKENS_KEY = "llm.followup_suggestion.max_tokens";
  private static final String DEFAULT_SYSTEM_PROMPT = """
      You suggest the next follow-up action for a private-domain postpartum recovery sales conversation.
      Return JSON only. Schema:
      {"followup_suggest":{"next_contact_at":"YYYY-MM-DDTHH:mm:ss","next_contact_direction":"short action direction"}}
      Use Asia/Shanghai business context. Suggest one practical next contact time and one concise direction.
      Do not invent medical diagnosis or guarantees.
      """;

  private final LlmService llmService;
  private final SystemConfigRepository configRepository;
  private final ObjectMapper objectMapper;

  public LlmFollowupSuggestionService(
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

  public Optional<CustomerMessageSentEvent.FollowupSuggestPayload> trySuggest(LlmFollowupSuggestionInput input) {
    if (!enabled()) {
      return Optional.empty();
    }
    LlmResponse response = llmService.generate(
        LlmScene.FOLLOWUP_SUGGESTION,
        input == null ? "" : input.leadType(),
        input == null ? "" : input.caller(),
        requestSummary(input),
        buildRequest(input));
    if (!response.success()) {
      return Optional.empty();
    }
    return parse(cleanJson(response.content()));
  }

  private LlmRequest buildRequest(LlmFollowupSuggestionInput input) {
    return new LlmRequest(
        systemPrompt(),
        userPrompt(input),
        List.of(),
        decimalConfig(TEMPERATURE_KEY),
        integerConfig(MAX_TOKENS_KEY));
  }

  private String userPrompt(LlmFollowupSuggestionInput input) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("leadType", input == null ? "" : nvl(input.leadType()));
    payload.put("nickname", input == null ? "" : nvl(input.nickname()));
    payload.put("conversationSummary", input == null ? "" : clip(input.conversationSummary(), 1200));
    payload.put("sentText", input == null ? "" : clip(input.sentText(), 500));
    payload.put("selectedDirection", input == null ? "" : nvl(input.selectedDirection()));
    payload.put("rawMessages", sanitizeMessages(input == null ? List.of() : input.rawMessages()));
    String phone = input == null ? "" : input.phone();
    if (phone != null && phone.length() >= 4) {
      payload.put("phoneLast4", phone.substring(phone.length() - 4));
    }
    payload.put("now", LocalDateTime.now().withNano(0).toString());
    return """
        Suggest the next follow-up time and direction for this confirmed customer reply.
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

  private Optional<CustomerMessageSentEvent.FollowupSuggestPayload> parse(String raw) {
    try {
      JsonNode root = objectMapper.readTree(raw);
      JsonNode node = root.path("followup_suggest");
      String nextAt = normalizeNextAt(text(node.path("next_contact_at")));
      String direction = text(node.path("next_contact_direction"));
      if (nextAt.isBlank() || direction == null || direction.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(new CustomerMessageSentEvent.FollowupSuggestPayload(nextAt, clip(direction, 120)));
    } catch (Exception ex) {
      log.warn("LLM followup suggestion parse failed: {}", ex.getMessage());
      return Optional.empty();
    }
  }

  private String normalizeNextAt(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.isBlank()) {
      return "";
    }
    try {
      return LocalDateTime.parse(value.replace(" ", "T")).withNano(0).toString();
    } catch (RuntimeException ignored) {
      // Try date-only value below.
    }
    try {
      return LocalDate.parse(value.substring(0, Math.min(value.length(), 10))).atTime(9, 0).toString();
    } catch (RuntimeException ex) {
      return "";
    }
  }

  private String systemPrompt() {
    return configRepository.findValue(SYSTEM_PROMPT_KEY)
        .filter(value -> !value.isBlank())
        .orElse(DEFAULT_SYSTEM_PROMPT);
  }

  private String requestSummary(LlmFollowupSuggestionInput input) {
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
