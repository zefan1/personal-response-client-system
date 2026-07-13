package com.privateflow.modules.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.ProfileUpdates;
import com.privateflow.modules.skill.parser.SkillResponseParser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LlmProfileExtractionService {

  private static final Logger log = LoggerFactory.getLogger(LlmProfileExtractionService.class);
  private static final String ENABLED_KEY = "llm.profile_extraction.enabled";
  private static final String FALLBACK_KEY = "llm.profile_extraction.fallback_to_skill";
  private static final String SYSTEM_PROMPT_KEY = "llm.profile_extraction.system_prompt";
  private static final String TEMPERATURE_KEY = "llm.profile_extraction.temperature";
  private static final String MAX_TOKENS_KEY = "llm.profile_extraction.max_tokens";
  private static final String DEFAULT_SYSTEM_PROMPT = """
      You extract structured profile updates from a private-domain postpartum recovery sales conversation.
      Return JSON only. Schema:
      {"profile_updates":{"fields":{"fieldName":{"value":"","confidence":"HIGH|MEDIUM|LOW"}}}}
      Use only target fields provided by the user. Extract only facts clearly supported by the conversation.
      HIGH means explicit and safe to write automatically; MEDIUM means likely but needs human confirmation.
      LOW or uncertain values should be omitted. Do not infer medical diagnosis.
      """;

  private final LlmService llmService;
  private final SkillResponseParser responseParser;
  private final SystemConfigRepository configRepository;
  private final ObjectMapper objectMapper;

  public LlmProfileExtractionService(
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

  public Optional<ProfileUpdates> tryExtract(ProfileExtractRequest request) {
    if (!enabled()) {
      return Optional.empty();
    }
    LlmResponse response = llmService.generate(
        LlmScene.PROFILE_EXTRACTION,
        leadType(request),
        request == null ? "" : request.caller(),
        requestSummary(request == null ? "" : request.conversationText(), request == null ? Map.of() : request.existingProfile()),
        buildRequest(request));
    if (!response.success()) {
      return Optional.empty();
    }
    ProfileUpdates updates = responseParser.parseProfileUpdatesOnly(cleanJson(response.content()));
    if (updates.fields() == null || updates.fields().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(updates);
  }

  private LlmRequest buildRequest(ProfileExtractRequest request) {
    return new LlmRequest(
        systemPrompt(),
        userPrompt(request),
        java.util.List.of(),
        decimalConfig(TEMPERATURE_KEY),
        integerConfig(MAX_TOKENS_KEY));
  }

  private String userPrompt(ProfileExtractRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("conversationText", request == null ? "" : clip(request.conversationText(), 2500));
    payload.put("existingProfile", sanitizedProfile(request == null ? Map.of() : request.existingProfile()));
    payload.put("targetFields", request == null ? java.util.List.of() : request.targetFields());
    return """
        Extract profile updates from this conversation.
        Input JSON:
        %s
        """.formatted(toJson(payload));
  }

  private Map<String, Object> sanitizedProfile(Map<String, Object> raw) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (raw == null) {
      return result;
    }
    raw.forEach((key, value) -> {
      if (key == null || value == null || "phone".equalsIgnoreCase(key)) {
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
    Object phone = raw.get("phone");
    if (phone instanceof String text && text.length() >= 4) {
      result.put("phoneLast4", text.substring(text.length() - 4));
    }
    return result;
  }

  private String systemPrompt() {
    return configRepository.findValue(SYSTEM_PROMPT_KEY)
        .filter(value -> !value.isBlank())
        .orElse(DEFAULT_SYSTEM_PROMPT);
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

  private String requestSummary(String conversationText, Map<String, Object> profile) {
    String text = conversationText == null ? "" : conversationText.replaceAll("\\d{11}", "[PHONE]");
    String summary = clip(text, 120);
    Object phone = profile == null ? null : profile.get("phone");
    if (phone instanceof String rawPhone && rawPhone.length() >= 4) {
      summary += "...[phone:" + rawPhone.substring(rawPhone.length() - 4) + "]";
    }
    return clip(summary, 180);
  }

  private String leadType(ProfileExtractRequest request) {
    if (request == null || request.existingProfile() == null) {
      return "";
    }
    Object value = request.existingProfile().get("leadType");
    return value == null ? "" : String.valueOf(value);
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
}
