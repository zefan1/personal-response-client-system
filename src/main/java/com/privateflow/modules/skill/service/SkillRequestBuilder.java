package com.privateflow.modules.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.LeadTypes;
import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.ProfileAnalysisContext;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.config.SkillConfig;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import com.privateflow.modules.tags.TagCandidateBuilder;
import com.privateflow.modules.tags.TagCandidatePurpose;
import com.privateflow.modules.tags.TagCategory;
import com.privateflow.modules.tags.TagValue;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SkillRequestBuilder {

  private static final Logger log = LoggerFactory.getLogger(SkillRequestBuilder.class);
  private static final String REPLY_TAG_GUIDANCE =
      "【当前客户标签使用规则】标签只用于调整回复方向、优先级和语气。"
          + "不得向客户描述内部标签、系统判断、把握度、证据、来源或锁定状态。"
          + "标签与客户当前消息或真实业务事实冲突时，以当前消息和业务事实为准。";
  private static final Pattern CUSTOMER_PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]*)}}");
  private final SkillConfigProvider configProvider;
  private final CustomerQueryService customerQueryService;
  private final TagCandidateBuilder tagCandidateBuilder;
  private final ObjectMapper objectMapper;
  private final SkillRuntimeRouter runtimeRouter;
  private final ProfileAnalysisPromptBuilder profileAnalysisPromptBuilder;

  public SkillRequestBuilder(
      SkillConfigProvider configProvider,
      CustomerQueryService customerQueryService,
      TagCandidateBuilder tagCandidateBuilder,
      ObjectMapper objectMapper,
      SkillRuntimeRouter runtimeRouter) {
    this(
        configProvider,
        customerQueryService,
        tagCandidateBuilder,
        objectMapper,
        runtimeRouter,
        new ProfileAnalysisPromptBuilder());
  }

  @Autowired
  public SkillRequestBuilder(
      SkillConfigProvider configProvider,
      CustomerQueryService customerQueryService,
      TagCandidateBuilder tagCandidateBuilder,
      ObjectMapper objectMapper,
      SkillRuntimeRouter runtimeRouter,
      ProfileAnalysisPromptBuilder profileAnalysisPromptBuilder) {
    this.configProvider = configProvider;
    this.customerQueryService = customerQueryService;
    this.tagCandidateBuilder = tagCandidateBuilder;
    this.objectMapper = objectMapper;
    this.runtimeRouter = runtimeRouter;
    this.profileAnalysisPromptBuilder = profileAnalysisPromptBuilder;
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
    payload.put("system_prompt", buildSystemPrompt(request.scene(), config, customer));
    payload.put("previous_suggestions", request.previousSuggestions());
    payload.put("current_tags", request.currentTags());
    runtimeRouter.route(request.scene(), request.leadType(), config).ifPresent(skillId -> {
      payload.put("skill_id", skillId);
      payload.put("skill_group_id", skillId);
    });
    return payload;
  }

  public Map<String, Object> buildProfileExtract(ProfileExtractRequest request) {
    SkillConfig config = configProvider.get();
    ProfileAnalysisContext analysisContext = request.analysisContext();
    ProfileAnalysisPromptBuilder.ProfileAnalysisPrompt prompt = profileAnalysisPromptBuilder.build(
        request,
        config.redLines());
    String leadType = String.valueOf(analysisContext.customerProfile().getOrDefault(
        "leadType",
        request.existingProfile() == null ? "" : request.existingProfile().getOrDefault("leadType", "")));
    Map<String, Object> payload = new HashMap<>();
    payload.put("scene", Scene.PROFILE_EXTRACT.name());
    payload.put("lead_type", normalizeLeadType(leadType));
    payload.putAll(prompt.input());
    payload.put("system_prompt", prompt.systemPrompt());
    runtimeRouter.route(Scene.PROFILE_EXTRACT, leadType, config).ifPresent(skillId -> {
      payload.put("skill_id", skillId);
      payload.put("skill_group_id", skillId);
    });
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

  private String buildSystemPrompt(Scene scene, SkillConfig config, Map<String, Object> customer) {
    String template = config.systemPromptTemplate();
    for (String placeholder : List.of("{{red_lines}}", "{{available_tags}}", "{{scene}}")) {
      if (!template.contains(placeholder)) {
        log.warn("skill system prompt template missing placeholder {}", placeholder);
      }
    }
    String prompt = template
        .replace("{{red_lines}}", config.redLines() == null ? "" : config.redLines())
        .replace("{{available_tags}}", availableTags())
        .replace("{{scene}}", scene.name());
    return replaceCustomerPlaceholders(prompt, customer) + "\\n" + REPLY_TAG_GUIDANCE;
  }

  private String replaceCustomerPlaceholders(String prompt, Map<String, Object> customer) {
    Matcher matcher = CUSTOMER_PLACEHOLDER.matcher(prompt);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String key = matcher.group(1);
      if (List.of("red_lines", "available_tags", "scene").contains(key)) {
        matcher.appendReplacement(buffer, "");
        continue;
      }
      Object value = firstPresent(customer, key);
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  private Object firstPresent(Map<String, Object> customer, String key) {
    if (customer == null || customer.isEmpty()) {
      return null;
    }
    if (customer.containsKey(key)) {
      return customer.get(key);
    }
    return customer.get(toSnakeCase(key));
  }

  private String availableTags() {
    List<TagCategory> categories = tagCandidateBuilder.build(TagCandidatePurpose.SYSTEM_INFERENCE);
    if (categories.isEmpty()) {
      return "当前无可用标签";
    }
    StringBuilder builder = new StringBuilder();
    for (TagCategory category : categories) {
      builder.append("可用").append(category.categoryName()).append("标签: [");
      for (int i = 0; i < category.values().size(); i++) {
        TagValue value = category.values().get(i);
        if (i > 0) {
          builder.append(", ");
        }
        builder.append(value.displayName()).append("(").append(value.tagValue()).append(")");
      }
      builder.append("]\n");
    }
    return builder.toString().trim();
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
