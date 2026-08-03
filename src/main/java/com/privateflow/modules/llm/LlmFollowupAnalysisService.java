package com.privateflow.modules.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LlmFollowupAnalysisService {

  private static final Logger log = LoggerFactory.getLogger(LlmFollowupAnalysisService.class);
  private static final String ENABLED_KEY = "llm.followup_analysis.enabled";
  private static final String SYSTEM_PROMPT_KEY = "llm.followup_analysis.system_prompt";
  private static final String TEMPERATURE_KEY = "llm.followup_analysis.temperature";
  private static final String MAX_TOKENS_KEY = "llm.followup_analysis.max_tokens";
  private static final String LEGACY_ENABLED_KEY = "llm.summary.enabled";
  private static final String LEGACY_TEMPERATURE_KEY = "llm.summary.temperature";
  private static final String LEGACY_MAX_TOKENS_KEY = "llm.summary.max_tokens";
  private static final String DEFAULT_SYSTEM_PROMPT = """
      你负责根据客户完整旧档案和本次真实聊天，生成私域人员使用的客户跟进分析。
      只返回 JSON。客户原话是新增客户事实的唯一证据；员工回复、sentText、旧跟进记录和旧备注只能作为背景，
      不得把它们当成客户表达过的事实、顾虑、症状或承诺。
      JSON 字段：internal_note、body_concerns、customer_profile_summary、followup_record、
      customer_stage、next_followup_direction、next_followup_at、next_followup_time_explicit、tracking_capture。
      internal_note 是给私域人员的内部提醒；followup_record 是本次沟通事实摘要；tracking_capture 是本次新增的重要客户信号。
      客户原话明确说出身体困扰时，body_concerns 必须填写，只能忠实概括原话；
      客户原话新增产后阶段、需求或困扰时，customer_profile_summary 必须更新为简洁客户档案，tracking_capture 必须填写最重要的新信号。
      internal_note 只能给出基于客户原话的跟进提醒，不得把建议或可能性写成客户事实。
      例如客户只说“肚子大”时，不得把肚子大推断为腹直肌分离或其他诊断。
      必须结合旧档案更新信息；确实没有客户新证据的字段返回 null。只有客户原话明确说出下次联系日期或时间时，
      next_followup_time_explicit 才能为 true 并填写 next_followup_at，否则必须为 false 且 next_followup_at 为 null。
      不得编造医疗诊断、承诺或客户未表达的信息。
      """;
  private static final String BODY_CONCERN_REPAIR_PROMPT = """
      你只判断客户是否明确描述了自己的身体困扰。只返回 JSON：
      {"has_explicit_body_concern":true或false,"evidence_quotes":["客户原话中的最小连续片段"]}
      evidence_quotes 只能逐字复制客户原话，不能改写、概括、诊断或采用员工的话；每项只保留一个身体困扰。
      客户没有明确描述自己的身体困扰时，返回 false 和空数组。
      """;
  private static final Pattern FIRST_PERSON_STATE = Pattern.compile(
      "我|本人|自己|最近|一直|现在|总是|有点|觉得|感觉|产后");
  private static final Pattern BODY_CONCERN_SIGNAL = Pattern.compile(
      "腰(?:痛|疼|酸)|背(?:痛|疼|酸)|疼痛|酸痛|胀痛|漏尿|尿失禁|腹直肌分离|"
          + "耻骨(?:痛|疼)|骨盆(?:痛|疼)|肚子.{0,4}(?:大|松|软)|腹部.{0,4}(?:大|松|软)|"
          + "下垂|松弛");

  private final LlmService llmService;
  private final SystemConfigRepository configRepository;
  private final ObjectMapper objectMapper;

  public LlmFollowupAnalysisService(
      LlmService llmService,
      SystemConfigRepository configRepository,
      ObjectMapper objectMapper) {
    this.llmService = llmService;
    this.configRepository = configRepository;
    this.objectMapper = objectMapper;
  }

  public Optional<FollowupAnalysisPayload> tryAnalyze(LlmFollowupAnalysisInput input) {
    if (!enabled()) {
      return Optional.empty();
    }
    Customer customer = input == null ? null : input.customer();
    LlmResponse response = llmService.generate(
        LlmScene.SUMMARY,
        customer == null ? "" : nvl(customer.getLeadType()),
        input == null ? "" : nvl(input.caller()),
        requestSummary(input),
        new LlmRequest(
            systemPrompt(),
            userPrompt(input),
            List.of(),
            decimalConfig(TEMPERATURE_KEY, LEGACY_TEMPERATURE_KEY),
            integerConfig(MAX_TOKENS_KEY, LEGACY_MAX_TOKENS_KEY)));
    if (!response.success()) {
      return Optional.empty();
    }
    Optional<FollowupAnalysisPayload> parsed = parse(cleanJson(response.content()));
    if (parsed.isEmpty()) {
      return Optional.empty();
    }
    return repairMissingBodyConcerns(input, parsed.orElseThrow());
  }

  private Optional<FollowupAnalysisPayload> repairMissingBodyConcerns(
      LlmFollowupAnalysisInput input,
      FollowupAnalysisPayload payload) {
    Customer customer = input == null ? null : input.customer();
    if (!blank(payload.bodyConcerns())
        || (customer != null && !blank(customer.getBodyConcerns()))) {
      return Optional.of(payload);
    }
    List<String> customerMessages = customerMessages(input);
    if (customerMessages.isEmpty()) {
      return Optional.of(payload);
    }
    LlmResponse response = llmService.generate(
        LlmScene.SUMMARY,
        customer == null ? "" : nvl(customer.getLeadType()),
        input == null ? "" : nvl(input.caller()),
        repairRequestSummary(input),
        new LlmRequest(
            BODY_CONCERN_REPAIR_PROMPT,
            "Customer messages JSON:\n" + toJson(customerMessages),
            List.of(),
            0.0,
            300));
    if (!response.success()) {
      return Optional.empty();
    }
    Optional<String> repaired = parseBodyConcernEvidence(cleanJson(response.content()), customerMessages);
    if (repaired.isEmpty()) {
      return Optional.of(payload);
    }
    String bodyConcerns = repaired.orElseThrow();
    if (blank(bodyConcerns)) {
      return Optional.empty();
    }
    return Optional.of(new FollowupAnalysisPayload(
        payload.internalNote(),
        bodyConcerns,
        payload.customerProfileSummary(),
        payload.followupRecord(),
        payload.customerStage(),
        payload.nextFollowupDirection(),
        payload.nextFollowupAt(),
        payload.trackingCapture()));
  }

  private Optional<String> parseBodyConcernEvidence(String raw, List<String> customerMessages) {
    try {
      JsonNode root = objectMapper.readTree(raw);
      JsonNode explicit = root.get("has_explicit_body_concern");
      JsonNode quotes = root.get("evidence_quotes");
      if (explicit == null || !explicit.isBoolean() || quotes == null || !quotes.isArray()) {
        throw new IllegalArgumentException("body concern evidence response is incomplete");
      }
      if (!explicit.booleanValue()) {
        if (hasLikelyExplicitBodyConcern(customerMessages)) {
          throw new IllegalArgumentException("body concern evidence missed first-person symptom language");
        }
        return Optional.empty();
      }
      List<String> supportedQuotes = new java.util.ArrayList<>();
      for (JsonNode quoteNode : quotes) {
        String quote = text(quoteNode);
        if (blank(quote) || !isExactCustomerQuote(quote, customerMessages)) {
          throw new IllegalArgumentException("body concern evidence is not an exact customer quote");
        }
        if (!supportedQuotes.contains(quote)) {
          supportedQuotes.add(quote);
        }
      }
      if (supportedQuotes.isEmpty()) {
        throw new IllegalArgumentException("body concern evidence is empty");
      }
      return Optional.of(supportedQuotes.stream().collect(Collectors.joining("、")));
    } catch (Exception ex) {
      log.warn("LLM body concern evidence parse failed: {}", ex.getMessage());
      return Optional.of("");
    }
  }

  private boolean isExactCustomerQuote(String quote, List<String> customerMessages) {
    String normalizedQuote = normalizeWhitespace(quote);
    return customerMessages.stream()
        .map(this::normalizeWhitespace)
        .anyMatch(message -> message.contains(normalizedQuote));
  }

  private boolean hasLikelyExplicitBodyConcern(List<String> customerMessages) {
    return customerMessages.stream()
        .map(this::normalizeWhitespace)
        .anyMatch(message -> FIRST_PERSON_STATE.matcher(message).find()
            && BODY_CONCERN_SIGNAL.matcher(message).find());
  }

  private String normalizeWhitespace(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim();
  }

  private List<String> customerMessages(LlmFollowupAnalysisInput input) {
    return sanitizeMessages(input == null ? List.of() : input.rawMessages()).stream()
        .filter(message -> "customer".equals(message.get("role")))
        .map(message -> message.get("text"))
        .filter(text -> text != null && !text.isBlank())
        .toList();
  }

  private String repairRequestSummary(LlmFollowupAnalysisInput input) {
    String summary = "followup body concern evidence";
    Customer customer = input == null ? null : input.customer();
    if (customer != null && customer.getPhone() != null && customer.getPhone().length() >= 4) {
      summary += "...[phone:" + customer.getPhone().substring(customer.getPhone().length() - 4) + "]";
    }
    return summary;
  }

  private String userPrompt(LlmFollowupAnalysisInput input) {
    Customer customer = input == null ? null : input.customer();
    List<Map<String, String>> rawMessages = sanitizeMessages(input == null ? List.of() : input.rawMessages());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("customerProfile", customerProfile(customer));
    payload.put("rawMessages", rawMessages);
    payload.put("customerMessages", rawMessages.stream()
        .filter(message -> "customer".equals(message.get("role")))
        .map(message -> message.get("text"))
        .filter(text -> text != null && !text.isBlank())
        .toList());
    payload.put("sentText", input == null ? "" : clip(input.sentText(), 500));
    payload.put("selectedDirection", input == null ? "" : nvl(input.selectedDirection()));
    if (customer != null && customer.getPhone() != null && customer.getPhone().length() >= 4) {
      payload.put("phoneLast4", customer.getPhone().substring(customer.getPhone().length() - 4));
    }
    payload.put("now", LocalDateTime.now().withNano(0).toString());
    return """
        根据完整旧档案和本次聊天生成一次结构化跟进分析。员工 sentText 仅表示已发送内容，不能作为客户事实。
        Input JSON:
        %s
        """.formatted(toJson(payload));
  }

  private Map<String, Object> customerProfile(Customer customer) {
    Map<String, Object> profile = new LinkedHashMap<>();
    if (customer == null) {
      return profile;
    }
    put(profile, "nickname", customer.getNickname());
    put(profile, "sourceChannel", customer.getSourceChannel());
    put(profile, "leadType", customer.getLeadType());
    put(profile, "personalityType", customer.getPersonalityType());
    put(profile, "assignedKeeper", customer.getAssignedKeeper());
    put(profile, "intendedStore", customer.getIntendedStore());
    put(profile, "intendedProject", customer.getIntendedProject());
    put(profile, "purchasedProject", customer.getPurchasedProject());
    put(profile, "postpartumMonths", customer.getPostpartumMonths());
    put(profile, "parity", customer.getParity());
    put(profile, "deliveryMethod", customer.getDeliveryMethod());
    put(profile, "breastfeeding", customer.getBreastfeeding());
    put(profile, "lochiaPeriod", customer.getLochiaPeriod());
    put(profile, "pregnancyWeight", customer.getPregnancyWeight());
    put(profile, "currentWeight", customer.getCurrentWeight());
    put(profile, "bodyConcerns", customer.getBodyConcerns());
    put(profile, "diastasisRecti", customer.getDiastasisRecti());
    put(profile, "urineLeakage", customer.getUrineLeakage());
    put(profile, "pubicLumbago", customer.getPubicLumbago());
    put(profile, "prevRepairExp", customer.getPrevRepairExp());
    put(profile, "postpartumCheck", customer.getPostpartumCheck());
    put(profile, "exerciseHabits", customer.getExerciseHabits());
    put(profile, "intentLevel", customer.getIntentLevel());
    put(profile, "worries", customer.getWorries());
    put(profile, "customerStage", customer.getCustomerStage());
    put(profile, "followupNotes", customer.getFollowupNotes());
    put(profile, "nextFollowupAt", customer.getNextFollowupAt());
    put(profile, "nextFollowupDir", customer.getNextFollowupDir());
    put(profile, "internalNote", customer.getInternalNote());
    put(profile, "customerProfileSummary", customer.getCustomerProfileSummary());
    put(profile, "firstTrackingCapture", customer.getFirstTrackingCapture());
    put(profile, "secondTrackingCapture", customer.getSecondTrackingCapture());
    put(profile, "thirdTrackingCapture", customer.getThirdTrackingCapture());
    return profile;
  }

  private List<Map<String, String>> sanitizeMessages(List<CustomerMessageSentEvent.ChatMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return List.of();
    }
    return messages.stream()
        .skip(Math.max(0, messages.size() - 20))
        .map(message -> Map.of(
            "role", normalizedRole(message.role()),
            "text", clip(nvl(message.text()), 500),
            "timestamp", nvl(message.timestamp())))
        .toList();
  }

  private String normalizedRole(String role) {
    if (role == null) {
      return "unknown";
    }
    return switch (role.trim().toLowerCase()) {
      case "client", "customer", "user" -> "customer";
      case "employee", "assistant", "seller", "staff" -> "employee";
      default -> "unknown";
    };
  }

  private Optional<FollowupAnalysisPayload> parse(String raw) {
    try {
      JsonNode node = objectMapper.readTree(raw);
      String nextAt = node.path("next_followup_time_explicit").asBoolean(false)
          ? normalizeNextAt(text(node.path("next_followup_at")))
          : null;
      FollowupAnalysisPayload payload = new FollowupAnalysisPayload(
          clippedText(node, "internal_note", 500),
          clippedText(node, "body_concerns", 500),
          clippedText(node, "customer_profile_summary", 500),
          clippedText(node, "followup_record", 500),
          clippedText(node, "customer_stage", 100),
          clippedText(node, "next_followup_direction", 200),
          nextAt,
          clippedText(node, "tracking_capture", 500));
      return hasContent(payload) ? Optional.of(payload) : Optional.empty();
    } catch (Exception ex) {
      log.warn("LLM followup analysis parse failed: {}", ex.getMessage());
      return Optional.empty();
    }
  }

  private boolean hasContent(FollowupAnalysisPayload payload) {
    return !blank(payload.internalNote())
        || !blank(payload.bodyConcerns())
        || !blank(payload.customerProfileSummary())
        || !blank(payload.followupRecord())
        || !blank(payload.customerStage())
        || !blank(payload.nextFollowupDirection())
        || !blank(payload.nextFollowupAt())
        || !blank(payload.trackingCapture());
  }

  private String normalizeNextAt(String raw) {
    if (blank(raw)) {
      return null;
    }
    try {
      return LocalDateTime.parse(raw.trim().replace(" ", "T")).withNano(0).toString();
    } catch (RuntimeException ignored) {
      // Try a date-only explicit value below.
    }
    try {
      return LocalDate.parse(raw.trim().substring(0, Math.min(raw.trim().length(), 10))).atTime(9, 0).toString();
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private String clippedText(JsonNode node, String field, int maxLength) {
    String value = text(node.path(field));
    return value == null ? null : clip(value, maxLength);
  }

  private String text(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    String value = node.asText();
    return blank(value) || "null".equalsIgnoreCase(value) ? null : value.trim();
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

  private String requestSummary(LlmFollowupAnalysisInput input) {
    String summary = "followup analysis";
    Customer customer = input == null ? null : input.customer();
    if (customer != null && customer.getPhone() != null && customer.getPhone().length() >= 4) {
      summary += "...[phone:" + customer.getPhone().substring(customer.getPhone().length() - 4) + "]";
    }
    return summary;
  }

  private String systemPrompt() {
    return configRepository.findValue(SYSTEM_PROMPT_KEY)
        .filter(value -> !value.isBlank())
        .orElse(DEFAULT_SYSTEM_PROMPT);
  }

  public boolean enabled() {
    return configRepository.findValue(ENABLED_KEY)
        .or(() -> configRepository.findValue(LEGACY_ENABLED_KEY))
        .map(value -> "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()))
        .orElse(false);
  }

  private Double decimalConfig(String key, String fallbackKey) {
    return configRepository.findValue(key)
        .or(() -> configRepository.findValue(fallbackKey))
        .filter(value -> !value.isBlank()).map(value -> {
      try {
        return Double.parseDouble(value.trim());
      } catch (NumberFormatException ex) {
        return null;
      }
    }).orElse(null);
  }

  private Integer integerConfig(String key, String fallbackKey) {
    return configRepository.findValue(key)
        .or(() -> configRepository.findValue(fallbackKey))
        .filter(value -> !value.isBlank()).map(value -> {
      try {
        return Integer.parseInt(value.trim());
      } catch (NumberFormatException ex) {
        return null;
      }
    }).orElse(null);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      return "{}";
    }
  }

  private void put(Map<String, Object> target, String key, Object value) {
    if (value != null && (!(value instanceof String text) || !text.isBlank())) {
      target.put(key, value);
    }
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

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
