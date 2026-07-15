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
import org.springframework.stereotype.Component;

@Component
public class SkillRequestBuilder {

  private static final Logger log = LoggerFactory.getLogger(SkillRequestBuilder.class);
  private static final String PROFILE_ANALYSIS_TASK = """
      你是客户档案分析助手。请分析 profile_analysis_context 中的客户档案、当前标签、锁定分类和动态候选标签。
      只能把 recentMessages 中 role=client 的客户原话或明确业务数据作为判断依据，不能把员工回复当作客户证据。
      只能返回 target_fields 中的非标签档案字段；标签判断必须使用 candidateCategories 中当前提供的分类和标签编码。
      UPDATE 表示证据充分且满足分类策略；信息不足返回 UNABLE_TO_DETERMINE；当前值仍正确返回 KEEP_CURRENT。
      多选 ADD_ONLY 只能返回尚未存在的新增标签并使用 ADD；单选 REPLACE 使用 REPLACE；不修改时使用 NONE。
      """;
  private static final String PROFILE_ANALYSIS_OUTPUT_CONTRACT = """
      Return JSON only with this exact top-level schema:
      {
        "profile_updates": {
          "fields": {},
          "tag_decisions": [
            {
              "category_code": "category code from candidateCategories",
              "tag_codes": ["tag code from that category"],
              "confidence": 0.95,
              "evidence": "customer quote or business evidence",
              "result_type": "UPDATE|UNABLE_TO_DETERMINE|KEEP_CURRENT",
              "requested_action": "ADD|REPLACE|NONE"
            }
          ]
        }
      }
      Use only enabled candidates supplied in profile_analysis_context. Do not return chain-of-thought.
      """;
  private static final Pattern CUSTOMER_PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]*)}}");
  private final SkillConfigProvider configProvider;
  private final CustomerQueryService customerQueryService;
  private final TagCandidateBuilder tagCandidateBuilder;
  private final ObjectMapper objectMapper;
  private final SkillRuntimeRouter runtimeRouter;

  public SkillRequestBuilder(
      SkillConfigProvider configProvider,
      CustomerQueryService customerQueryService,
      TagCandidateBuilder tagCandidateBuilder,
      ObjectMapper objectMapper,
      SkillRuntimeRouter runtimeRouter) {
    this.configProvider = configProvider;
    this.customerQueryService = customerQueryService;
    this.tagCandidateBuilder = tagCandidateBuilder;
    this.objectMapper = objectMapper;
    this.runtimeRouter = runtimeRouter;
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
    runtimeRouter.route(request.scene(), request.leadType(), config).ifPresent(skillId -> {
      payload.put("skill_id", skillId);
      payload.put("skill_group_id", skillId);
    });
    return payload;
  }

  public Map<String, Object> buildProfileExtract(ProfileExtractRequest request) {
    SkillConfig config = configProvider.get();
    ProfileAnalysisContext analysisContext = request.analysisContext();
    String leadType = String.valueOf(analysisContext.customerProfile().getOrDefault(
        "leadType",
        request.existingProfile() == null ? "" : request.existingProfile().getOrDefault("leadType", "")));
    Map<String, Object> payload = new HashMap<>();
    payload.put("scene", Scene.PROFILE_EXTRACT.name());
    payload.put("lead_type", normalizeLeadType(leadType));
    payload.put("client_message", profileClientMessage(analysisContext));
    payload.put("chat_context", analysisContext.recentMessages());
    payload.put("customer", analysisContext.customerProfile());
    payload.put("target_fields", request.targetFields() == null ? List.of() : request.targetFields());
    payload.put("profile_analysis_context", analysisContext);
    payload.put("system_prompt", buildProfileSystemPrompt(config));
    runtimeRouter.route(Scene.PROFILE_EXTRACT, leadType, config).ifPresent(skillId -> {
      payload.put("skill_id", skillId);
      payload.put("skill_group_id", skillId);
    });
    return payload;
  }

  private String buildProfileSystemPrompt(SkillConfig config) {
    String redLines = config.redLines() == null ? "" : config.redLines().strip();
    return PROFILE_ANALYSIS_TASK.strip()
        + (redLines.isBlank() ? "" : "\n\n业务红线：\n" + redLines)
        + "\n\n"
        + PROFILE_ANALYSIS_OUTPUT_CONTRACT.strip();
  }

  private String profileClientMessage(ProfileAnalysisContext context) {
    return context.recentMessages().stream()
        .filter(message -> "client".equals(message.role()))
        .map(ProfileAnalysisContext.ConversationMessage::text)
        .filter(text -> text != null && !text.isBlank())
        .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
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
    return replaceCustomerPlaceholders(prompt, customer);
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
