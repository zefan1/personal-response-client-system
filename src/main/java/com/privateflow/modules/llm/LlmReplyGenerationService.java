package com.privateflow.modules.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.parser.SkillResponseParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LlmReplyGenerationService {

  private static final Logger log = LoggerFactory.getLogger(LlmReplyGenerationService.class);
  private static final String ENABLED_KEY = "llm.reply_generation.enabled";
  private static final String FALLBACK_KEY = "llm.reply_generation.fallback_to_skill";
  private static final String SYSTEM_PROMPT_KEY = "llm.reply_generation.system_prompt";
  private static final String TEMPERATURE_KEY = "llm.reply_generation.temperature";
  private static final String MAX_TOKENS_KEY = "llm.reply_generation.max_tokens";
  private static final String REPLY_TAG_GUIDANCE =
      "【当前客户标签使用规则】标签只用于调整回复方向、优先级和语气。"
          + "不得向客户描述内部标签、系统判断、把握度、证据、来源或锁定状态。"
          + "标签与客户当前消息或真实业务事实冲突时，以当前消息和真实业务事实为准。";
  private static final String DEFAULT_SYSTEM_PROMPT = """
      You generate reply suggestions for a private-domain postpartum recovery sales assistant.
      Return JSON only. Schema:
      {"suggestions":[{"text":"ready-to-send reply","direction":"OPENING|NEXT_STEP|ANSWER|SOFT_CLOSE","reason":"short reason"}],"customer_analysis":{"intent":"","emotion":"","personality_type_suggest":"","confidence":""},"followup_suggest":{"next_contact_at":"","next_contact_direction":""}}
      Rules: suggestions must contain exactly 3 items; text should be ready to send to the customer; use Simplified Chinese unless the customer used another language; be warm, concise and professional; do not invent medical diagnosis or guarantees; avoid exposing internal notes.
      Current customer tags may guide reply direction, priority and tone only; never disclose internal tag names, system judgments, confidence, evidence, source or lock state.
      """;

  private final LlmService llmService;
  private final SkillResponseParser responseParser;
  private final SystemConfigRepository configRepository;
  private final ObjectMapper objectMapper;

  public LlmReplyGenerationService(
      LlmService llmService,
      SkillResponseParser responseParser,
      SystemConfigRepository configRepository,
      ObjectMapper objectMapper) {
    this.llmService = llmService;
    this.responseParser = responseParser;
    this.configRepository = configRepository;
    this.objectMapper = objectMapper;
  }

  public boolean enabled() {
    return booleanConfig(ENABLED_KEY, false);
  }

  public boolean fallbackToSkill() {
    return booleanConfig(FALLBACK_KEY, true);
  }

  public Optional<SkillResponse> tryGenerate(SkillRequest request) {
    if (!enabled()) {
      return Optional.empty();
    }
    LlmResponse response = llmService.generate(
        LlmScene.REPLY_GENERATION,
        request.leadType(),
        request.caller(),
        requestSummary(request.clientMessage(), request.phone()),
        buildRequest(request));
    if (!response.success()) {
      return Optional.empty();
    }
    try {
      return Optional.of(responseParser.parseReplies(cleanJson(response.content())));
    } catch (RuntimeException ex) {
      log.warn("LLM reply generation response parse failed: {}", ex.getMessage());
      return Optional.empty();
    }
  }

  private LlmRequest buildRequest(SkillRequest request) {
    return new LlmRequest(
        systemPrompt(),
        userPrompt(request),
        List.of(),
        decimalConfig(TEMPERATURE_KEY),
        integerConfig(MAX_TOKENS_KEY));
  }

  private String userPrompt(SkillRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("scene", request.scene() == null ? "" : request.scene().name());
    payload.put("leadType", nvl(request.leadType()));
    payload.put("customer", sanitizedCustomer(request.customer(), request.phone()));
    payload.put("clientMessage", nvl(request.clientMessage()));
    payload.put("chatContext", sanitizeChatContext(request.chatContext()));
    payload.put("previousSuggestions", request.previousSuggestions() == null ? List.of() : request.previousSuggestions());
    payload.put("currentTags", request.currentTags() == null ? List.of() : request.currentTags());
    return ("""
        Generate reply suggestions for this customer conversation.
        Input JSON:
        %s
        """.formatted(toJson(payload))) + "\n" + REPLY_TAG_GUIDANCE;
  }

  private Map<String, Object> sanitizedCustomer(Map<String, Object> raw, String phone) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (raw != null) {
      raw.forEach((key, value) -> {
        if (value == null || "phone".equalsIgnoreCase(key)) {
          return;
        }
        if (value instanceof String text) {
          if (!text.isBlank()) {
            result.put(key, clip(text, 300));
          }
          return;
        }
        result.put(key, value);
      });
    }
    if (phone != null && phone.length() >= 4) {
      result.put("phoneLast4", phone.substring(phone.length() - 4));
    }
    return result;
  }

  private List<Map<String, String>> sanitizeChatContext(List<Map<String, String>> chatContext) {
    if (chatContext == null || chatContext.isEmpty()) {
      return List.of();
    }
    return chatContext.stream()
        .skip(Math.max(0, chatContext.size() - 8))
        .map(item -> Map.of(
            "role", llmRole(item.get("role")),
            "text", clip(nvl(item.get("text")), 500)))
        .toList();
  }

  private String llmRole(String role) {
    if ("keeper".equalsIgnoreCase(role) || "assistant".equalsIgnoreCase(role)) {
      return "assistant";
    }
    return "user";
  }

  private String systemPrompt() {
    String configured = configRepository.findValue(SYSTEM_PROMPT_KEY)
        .filter(value -> !value.isBlank())
        .orElse(DEFAULT_SYSTEM_PROMPT);
    return configured + "\n" + REPLY_TAG_GUIDANCE;
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

  private String requestSummary(String clientMessage, String phone) {
    String text = clientMessage == null ? "" : clientMessage.replaceAll("\\d{11}", "[PHONE]");
    String clipped = clip(text, 100);
    if (phone != null && phone.length() >= 4) {
      clipped += "...[phone:" + phone.substring(phone.length() - 4) + "]";
    }
    return clip(clipped, 150);
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
