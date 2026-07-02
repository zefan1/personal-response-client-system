package com.privateflow.modules.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.LeadTypes;
import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.config.SkillConfig;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import com.privateflow.modules.skill.infra.PersonalityTagRepository;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SkillRequestBuilder {

  private static final Logger log = LoggerFactory.getLogger(SkillRequestBuilder.class);
  private final SkillConfigProvider configProvider;
  private final CustomerQueryService customerQueryService;
  private final PersonalityTagRepository tagRepository;
  private final ObjectMapper objectMapper;

  public SkillRequestBuilder(
      SkillConfigProvider configProvider,
      CustomerQueryService customerQueryService,
      PersonalityTagRepository tagRepository,
      ObjectMapper objectMapper) {
    this.configProvider = configProvider;
    this.customerQueryService = customerQueryService;
    this.tagRepository = tagRepository;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> build(SkillRequest request) {
    SkillConfig config = configProvider.get();
    Map<String, Object> customer = new HashMap<>();
    if (request.customer() != null) {
      customer.putAll(request.customer());
    }
    String phone = request.phone();
    if ((customer.isEmpty() || !customer.containsKey("phone")) && phone != null && !phone.isBlank()) {
      Customer loaded = customerQueryService.getByPhone(phone);
      if (loaded != null) {
        customer.putAll(toSnakeCaseCustomer(loaded));
      }
    }
    if (phone == null && customer.get("phone") instanceof String existingPhone) {
      phone = existingPhone;
    }
    if (phone != null && !phone.isBlank()) {
      customer.put("phone", transferPhone(phone, config));
    }
    Map<String, Object> payload = new HashMap<>();
    payload.put("scene", request.scene().name());
    payload.put("lead_type", normalizeLeadType(request.leadType()));
    payload.put("client_message", request.clientMessage());
    payload.put("chat_context", sanitizeChatContext(request.chatContext()));
    payload.put("customer", customer);
    payload.put("system_prompt", buildSystemPrompt(request.scene(), config));
    payload.put("previous_suggestions", request.previousSuggestions());
    payload.put("skill_group_id", selectSkillGroup(request.leadType(), config));
    return payload;
  }

  public Map<String, Object> buildProfileExtract(ProfileExtractRequest request) {
    SkillConfig config = configProvider.get();
    Map<String, Object> payload = new HashMap<>();
    payload.put("scene", Scene.PROFILE_EXTRACT.name());
    payload.put("lead_type", null);
    payload.put("client_message", request.conversationText());
    payload.put("chat_context", List.of());
    payload.put("customer", request.existingProfile() == null ? Map.of() : request.existingProfile());
    payload.put("target_fields", request.targetFields() == null ? List.of() : request.targetFields());
    payload.put("system_prompt", buildSystemPrompt(Scene.PROFILE_EXTRACT, config));
    payload.put("skill_group_id", config.defaultSkillId());
    return payload;
  }

  public String requestSummary(String clientMessage, String phone) {
    String text = clientMessage == null ? "" : clientMessage.replaceAll("\\d{11}", "[PHONE]");
    String clipped = text.substring(0, Math.min(text.length(), 100));
    if (phone != null && phone.length() >= 4) {
      clipped += "...[phone:" + phone.substring(phone.length() - 4) + "]";
    }
    return clipped.substring(0, Math.min(clipped.length(), 150));
  }

  private String normalizeLeadType(String leadType) {
    String normalized = LeadTypes.normalize(leadType);
    return LeadTypes.TUAN_GOU.equals(normalized) || LeadTypes.XIAN_SUO.equals(normalized) || LeadTypes.PENDING.equals(normalized)
        ? normalized
        : null;
  }

  private String selectSkillGroup(String leadType, SkillConfig config) {
    String normalized = normalizeLeadType(leadType);
    if (LeadTypes.TUAN_GOU.equals(normalized)) {
      return config.tuanSkillGroupId();
    }
    if (LeadTypes.XIAN_SUO.equals(normalized)) {
      return config.xiansuoSkillGroupId();
    }
    return config.defaultSkillId();
  }

  private String buildSystemPrompt(Scene scene, SkillConfig config) {
    String template = config.systemPromptTemplate();
    for (String placeholder : List.of("{{red_lines}}", "{{available_tags}}", "{{scene}}")) {
      if (!template.contains(placeholder)) {
        log.warn("skill system prompt template missing placeholder {}", placeholder);
      }
    }
    return template
        .replace("{{red_lines}}", config.redLines() == null ? "" : config.redLines())
        .replace("{{available_tags}}", availableTags())
        .replace("{{scene}}", scene.name());
  }

  private String availableTags() {
    try {
      List<PersonalityTagRepository.PersonalityTag> tags = tagRepository.findEnabled();
      if (tags.isEmpty()) {
        return "当前无可用标签";
      }
      StringBuilder builder = new StringBuilder();
      for (PersonalityTagRepository.PersonalityTag tag : tags) {
        builder.append(tag.value()).append(": ").append(tag.label());
        if (tag.description() != null && !tag.description().isBlank()) {
          builder.append("/").append(tag.description());
        }
        builder.append("\n");
      }
      return builder.toString().trim();
    } catch (RuntimeException ex) {
      return "当前无可用标签";
    }
  }

  private String transferPhone(String phone, SkillConfig config) {
    if ("ENCRYPTED_FULL".equals(config.phoneTransferMode())) {
      log.error("ENCRYPTED_FULL phone transfer requested but encryption integration is not configured, fallback to LAST_FOUR");
    }
    return phone.length() <= 4 ? phone : phone.substring(phone.length() - 4);
  }

  private List<Map<String, String>> sanitizeChatContext(List<Map<String, String>> chatContext) {
    if (chatContext == null || chatContext.isEmpty()) {
      return List.of();
    }
    return chatContext.stream()
        .filter(item -> "client".equals(item.get("role")) || "keeper".equals(item.get("role")))
        .skip(Math.max(0, chatContext.size() - 6))
        .toList();
  }

  private Map<String, Object> toSnakeCaseCustomer(Customer customer) {
    Map<String, Object> raw = objectMapper.convertValue(customer, Map.class);
    Map<String, Object> result = new HashMap<>();
    raw.forEach((key, value) -> {
      if (value != null && !(value instanceof String text && text.isBlank())) {
        result.put(toSnakeCase(key), value);
      }
    });
    return result;
  }

  private String toSnakeCase(String camel) {
    return camel.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
  }
}
