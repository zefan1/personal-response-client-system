package com.privateflow.modules.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.profile.service.TagAnalysisDecisionValidator;
import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.parser.SkillProfileAnalysisResponseParser;
import com.privateflow.modules.skill.service.ProfileAnalysisPromptBuilder;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LlmProfileExtractionService {

  private static final String ENABLED_KEY = "llm.profile_extraction.enabled";
  private static final String FALLBACK_KEY = "llm.profile_extraction.fallback_to_skill";
  private static final String SYSTEM_PROMPT_KEY = "llm.profile_extraction.system_prompt";
  private static final String TEMPERATURE_KEY = "llm.profile_extraction.temperature";
  private static final String MAX_TOKENS_KEY = "llm.profile_extraction.max_tokens";

  private final LlmService llmService;
  private final ProfileAnalysisPromptBuilder promptBuilder;
  private final SkillProfileAnalysisResponseParser responseParser;
  private final TagAnalysisDecisionValidator decisionValidator;
  private final SystemConfigRepository configRepository;
  private final ObjectMapper objectMapper;

  public LlmProfileExtractionService(
      LlmService llmService,
      ProfileAnalysisPromptBuilder promptBuilder,
      SkillProfileAnalysisResponseParser responseParser,
      TagAnalysisDecisionValidator decisionValidator,
      SystemConfigRepository configRepository,
      ObjectMapper objectMapper) {
    this.llmService = llmService;
    this.promptBuilder = promptBuilder;
    this.responseParser = responseParser;
    this.decisionValidator = decisionValidator;
    this.configRepository = configRepository;
    this.objectMapper = objectMapper;
  }

  public boolean enabled() {
    return booleanConfig(ENABLED_KEY, false);
  }

  public boolean fallbackToSkill() {
    return booleanConfig(FALLBACK_KEY, true);
  }

  public Optional<ProfileAnalysisResult> tryExtract(ProfileExtractRequest request) {
    if (!enabled()) {
      return Optional.empty();
    }
    String conversationText = request == null ? "" : request.conversationText();
    Map<String, Object> existingProfile = request == null ? Map.of() : request.existingProfile();
    return llmService.generateValidated(
        LlmScene.PROFILE_EXTRACTION,
        leadType(request),
        request == null ? "" : request.caller(),
        requestSummary(conversationText, existingProfile),
        buildRequest(request),
        content -> decisionValidator.validate(
            responseParser.parse(cleanJson(content)),
            request));
  }

  public LlmProfileExtractionTestResult test(ProfileExtractRequest request, LlmConfig config) {
    LlmResponse response = llmService.test(buildRequest(request), config);
    if (!response.success()) {
      return new LlmProfileExtractionTestResult(
          false,
          response.elapsedMs(),
          response.model(),
          response.protocol(),
          null,
          response.errorCode(),
          response.message());
    }
    try {
      ProfileAnalysisResult analysis = decisionValidator.validate(
          responseParser.parse(cleanJson(response.content())),
          request);
      return new LlmProfileExtractionTestResult(
          true,
          response.elapsedMs(),
          response.model(),
          response.protocol(),
          analysis,
          null,
          null);
    } catch (RuntimeException ex) {
      return new LlmProfileExtractionTestResult(
          false,
          response.elapsedMs(),
          response.model(),
          response.protocol(),
          null,
          LlmErrorCodes.RESPONSE_INVALID,
          ex.getMessage());
    }
  }

  private LlmRequest buildRequest(ProfileExtractRequest request) {
    ProfileAnalysisPromptBuilder.ProfileAnalysisPrompt prompt = promptBuilder.build(
        request,
        additionalInstructions());
    return new LlmRequest(
        prompt.systemPrompt(),
        "Profile analysis input JSON:\n" + toJson(prompt.input()),
        java.util.List.of(),
        decimalConfig(TEMPERATURE_KEY),
        integerConfig(MAX_TOKENS_KEY));
  }

  private String additionalInstructions() {
    return configRepository.findValue(SYSTEM_PROMPT_KEY)
        .filter(value -> !value.isBlank())
        .orElse("");
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("档案分析上下文序列化失败", ex);
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
    if (request == null || request.analysisContext() == null) {
      return "";
    }
    Object contextLeadType = request.analysisContext().customerProfile().get("leadType");
    if (contextLeadType != null) {
      return String.valueOf(contextLeadType);
    }
    if (request.existingProfile() == null) {
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
