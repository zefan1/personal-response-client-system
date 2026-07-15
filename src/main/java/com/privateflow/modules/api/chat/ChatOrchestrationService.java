package com.privateflow.modules.api.chat;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.image.ImageRecognitionException;
import com.privateflow.modules.image.ImageRecognitionService;
import com.privateflow.modules.image.Message;
import com.privateflow.modules.image.RecognitionResult;
import com.privateflow.modules.image.Source;
import com.privateflow.modules.llm.LlmReplyGenerationService;
import com.privateflow.modules.llm.LlmFollowupSuggestionInput;
import com.privateflow.modules.llm.LlmFollowupSuggestionService;
import com.privateflow.modules.llm.LlmSummaryInput;
import com.privateflow.modules.llm.LlmSummaryService;
import com.privateflow.modules.match.MatchRequest;
import com.privateflow.modules.match.MatchResult;
import com.privateflow.modules.match.MatchType;
import com.privateflow.modules.match.CustomerMatchService;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillGatewayService;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class ChatOrchestrationService {

  private static final int FALLBACK_CONVERSATION_SUMMARY_MAX_CHARS = 2000;

  private final ImageRecognitionService imageRecognitionService;
  private final CustomerMatchService customerMatchService;
  private final SkillGatewayService skillGatewayService;
  private final CustomerQueryService customerQueryService;
  private final CustomerAccessService customerAccessService;
  private final RequestContextStore contextStore;
  private final ApplicationEventPublisher eventPublisher;
  private final AuditLogger auditLogger;
  private final SkillConfigProvider skillConfigProvider;
  private final LlmReplyGenerationService llmReplyGenerationService;
  private final LlmFollowupSuggestionService llmFollowupSuggestionService;
  private final LlmSummaryService llmSummaryService;

  public ChatOrchestrationService(
      ImageRecognitionService imageRecognitionService,
      CustomerMatchService customerMatchService,
      SkillGatewayService skillGatewayService,
      CustomerQueryService customerQueryService,
      CustomerAccessService customerAccessService,
      RequestContextStore contextStore,
      ApplicationEventPublisher eventPublisher,
      AuditLogger auditLogger,
      SkillConfigProvider skillConfigProvider,
      LlmReplyGenerationService llmReplyGenerationService,
      LlmFollowupSuggestionService llmFollowupSuggestionService,
      LlmSummaryService llmSummaryService) {
    this.imageRecognitionService = imageRecognitionService;
    this.customerMatchService = customerMatchService;
    this.skillGatewayService = skillGatewayService;
    this.customerQueryService = customerQueryService;
    this.customerAccessService = customerAccessService;
    this.contextStore = contextStore;
    this.eventPublisher = eventPublisher;
    this.auditLogger = auditLogger;
    this.skillConfigProvider = skillConfigProvider;
    this.llmReplyGenerationService = llmReplyGenerationService;
    this.llmFollowupSuggestionService = llmFollowupSuggestionService;
    this.llmSummaryService = llmSummaryService;
  }

  public ChatResponse recognize(ChatRecognizeRequest request) {
    if (request == null || (blank(request.imageBase64()) && blank(request.textMessage()))) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "please provide screenshot or chat text");
    }
    RecognitionResult recognized = recognizeImage(request.imageBase64());
    String nickname = firstNonBlank(request.customerIdentifier(), recognized == null ? null : recognized.nickname());
    String phone = recognized == null ? null : recognized.phone();
    MatchResult match = match(nickname, phone, request.leadType(), request.sourceTable());
    Customer customer = firstCustomer(match);
    String clientMessage = buildClientMessage(request, recognized);
    List<Map<String, String>> chatContext = messages(request, recognized);
    GeneratedReplies generated = generateSkill(Scene.CHAT_RECOGNIZE, request.leadType(), customer, phone, clientMessage, List.of(), chatContext);
    String responsePhone = customer == null ? phone : customer.getPhone();
    saveContext(responsePhone, clientMessage, generated.skill(), Scene.CHAT_RECOGNIZE, customer, request.leadType(), chatContext, 0);
    auditLogger.log("CALL_SKILL", AuthContext.username(), "CHAT", responsePhone, "chat recognize");
    return new ChatResponse(responsePhone, nickname, match.matchType() == MatchType.NONE, match, generated.skill(), null, generated.source());
  }

  public ChatResponse generate(GenerateRequest request) {
    if (request == null || blank(request.phone())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "phone is required");
    }
    Customer customer = customerQueryService.getByPhone(request.phone());
    if (customer == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "customer not found");
    }
    Scene scene = "OPENING".equalsIgnoreCase(request.scene()) ? Scene.OPENING : Scene.ACTIVE_REPLY;
    String clientMessage = blank(request.clientMessage()) ? customer.getFollowupNotes() : request.clientMessage();
    GeneratedReplies generated = generateSkill(scene, customer.getLeadType(), customer, customer.getPhone(), clientMessage, List.of(), List.of());
    saveContext(customer.getPhone(), clientMessage, generated.skill(), scene, customer, customer.getLeadType(), List.of(), 0);
    return new ChatResponse(customer.getPhone(), customer.getNickname(), false, null, generated.skill(), null, generated.source());
  }

  public ChatResponse regenerate(RegenerateRequest request) {
    if (request == null || blank(request.phone())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "phone is required");
    }
    RequestContext context = contextStore.read(AuthContext.username(), request.phone()).orElse(null);
    if (context == null) {
      return generate(new GenerateRequest(request.phone(), "ACTIVE_REPLY", null));
    }
    SkillRequest previous = context.request();
    List<String> previousSuggestions = context.response() == null || context.response().suggestions() == null
        ? List.of()
        : context.response().suggestions().stream().map(s -> s.text()).toList();
    SkillRequest next = new SkillRequest(
        Scene.REGENERATE,
        previous.leadType(),
        previous.phone(),
        previous.clientMessage(),
        previous.customer(),
        previous.systemPrompt(),
        previousSuggestions,
        previous.chatContext(),
        AuthContext.username());
    GeneratedReplies generated = generateReplies(next);
    int count = context.regenerateCount() + 1;
    contextStore.save(AuthContext.username(), request.phone(), new RequestContext(next, generated.skill(), count));
    String warning = regenerateWarning(count);
    return new ChatResponse(request.phone(), null, false, null, generated.skill(), warning, generated.source());
  }

  public Map<String, Object> sendConfirm(SendConfirmRequest request) {
    if (request == null || blank(request.phone()) || blank(request.sentText())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "phone and sentText are required");
    }
    requireSendConfirmAccess(request);
    List<CustomerMessageSentEvent.ChatMessage> rawMessages = sendConfirmMessages(request);
    String conversationSummary = conversationSummary(request, rawMessages);
    CustomerMessageSentEvent.FollowupSuggestPayload followupSuggest = request.followupSuggest() == null
        ? llmFollowupSuggestionService.trySuggest(new LlmFollowupSuggestionInput(
            request.phone(),
            request.nickname(),
            request.leadType(),
            conversationSummary,
            rawMessages,
            request.sentText(),
            request.selectedDirection(),
            AuthContext.username())).orElse(null)
        : request.followupSuggest();
    eventPublisher.publishEvent(new CustomerMessageSentEvent(
        request.phone(),
        request.nickname(),
        request.isNewCustomer(),
        request.sourceTable(),
        request.leadType(),
        conversationSummary,
        rawMessages,
        request.sentText(),
        request.selectedDirection(),
        followupSuggest,
        AuthContext.username()));
    auditLogger.log("SEND_CONFIRM", AuthContext.username(), "CUSTOMER", request.phone(), "message sent");
    return Map.of("accepted", true);
  }

  private String conversationSummary(SendConfirmRequest request, List<CustomerMessageSentEvent.ChatMessage> rawMessages) {
    if (!blank(request.conversationSummary())) {
      return request.conversationSummary();
    }
    return llmSummaryService.trySummarize(new LlmSummaryInput(
        request.phone(),
        request.nickname(),
        request.leadType(),
        rawMessages,
        request.sentText(),
        request.selectedDirection(),
        AuthContext.username()))
        .orElseGet(() -> customerMessageSummary(rawMessages));
  }

  private void requireSendConfirmAccess(SendConfirmRequest request) {
    Customer customer = customerQueryService.getByPhone(request.phone());
    if (customer != null) {
      if (!customerAccessService.canAccess(customer)) {
        throw new ApiException(ApiErrorCodes.FORBIDDEN, "无权操作该客户");
      }
      return;
    }
    if (!request.isNewCustomer()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "客户不存在");
    }
    if (contextStore.read(AuthContext.username(), request.phone()).isEmpty()) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "无权创建该客户记录");
    }
  }

  private String customerMessageSummary(List<CustomerMessageSentEvent.ChatMessage> rawMessages) {
    if (rawMessages == null || rawMessages.isEmpty()) {
      return "";
    }
    String summary = rawMessages.stream()
        .filter(message -> message != null && !blank(message.text()))
        .filter(message -> "client".equalsIgnoreCase(message.role()) || "customer".equalsIgnoreCase(message.role()))
        .map(CustomerMessageSentEvent.ChatMessage::text)
        .map(String::trim)
        .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
    return summary.substring(0, Math.min(summary.length(), FALLBACK_CONVERSATION_SUMMARY_MAX_CHARS));
  }

  private RecognitionResult recognizeImage(String imageBase64) {
    if (blank(imageBase64)) {
      return null;
    }
    try {
      return imageRecognitionService.recognize(Base64.getDecoder().decode(imageBase64), Source.BUTTON_CLICK);
    } catch (IllegalArgumentException ex) {
      throw new ApiException("30-10002", "图片格式不支持，请重新截图或使用 PNG/JPG");
    } catch (ImageRecognitionException ex) {
      throw new ApiException(ex.getErrorCode(), ex.getMessage());
    } catch (RuntimeException ex) {
      throw new ApiException("30-10001", "图片识别失败，请使用文字通道后重新生成回复");
    }
  }

  private MatchResult match(String nickname, String phone, String leadType, String sourceTable) {
    try {
      return customerMatchService.match(new MatchRequest(nickname, phone, leadType, sourceTable, AuthContext.username()));
    } catch (RuntimeException ex) {
      return MatchResult.none();
    }
  }

  private GeneratedReplies generateSkill(Scene scene, String leadType, Customer customer, String phone, String clientMessage, List<String> previousSuggestions, List<Map<String, String>> chatContext) {
    SkillRequest skillRequest = new SkillRequest(
        scene,
        leadType,
        phone,
        clientMessage,
        customerMap(customer),
        Map.of(),
        previousSuggestions,
        chatContext,
        AuthContext.username());
    return generateReplies(skillRequest);
  }

  private GeneratedReplies generateReplies(SkillRequest skillRequest) {
    return llmReplyGenerationService.tryGenerate(skillRequest)
        .map(skill -> new GeneratedReplies(skill, ChatReplySource.llm()))
        .orElseGet(() -> {
          if (!llmReplyGenerationService.fallbackToSkill()) {
            return new GeneratedReplies(new SkillResponse(List.of(), null, null, null), ChatReplySource.fallback("LLM 回复生成失败，且未启用 Skill 回落"));
          }
          SkillResponse skill = skillGatewayService.generateReplies(skillRequest);
          return new GeneratedReplies(skill, replySourceForSkill(skill));
        });
  }

  private ChatReplySource replySourceForSkill(SkillResponse skill) {
    if (skill == null || skill.suggestions() == null || skill.suggestions().isEmpty()) {
      return ChatReplySource.fallback("Skill 未返回可用回复");
    }
    String direction = skill.suggestions().get(0).direction();
    if ("SYSTEM_FALLBACK".equalsIgnoreCase(direction)) {
      return ChatReplySource.fallback("Skill 不可用，已使用系统降级回复");
    }
    return ChatReplySource.skill();
  }

  private void saveContext(
      String phone,
      String clientMessage,
      SkillResponse skill,
      Scene scene,
      Customer customer,
      String leadType,
      List<Map<String, String>> chatContext,
      int regenerateCount) {
    if (!blank(phone)) {
      SkillRequest skillRequest = new SkillRequest(
          scene,
          leadType,
          phone,
          clientMessage,
          customerMap(customer),
          Map.of(),
          List.of(),
          chatContext == null ? List.of() : List.copyOf(chatContext),
          AuthContext.username());
      contextStore.save(AuthContext.username(), phone, new RequestContext(skillRequest, skill, regenerateCount));
    }
  }

  private List<CustomerMessageSentEvent.ChatMessage> sendConfirmMessages(SendConfirmRequest request) {
    List<CustomerMessageSentEvent.ChatMessage> storedMessages = contextStore.read(AuthContext.username(), request.phone())
        .map(RequestContext::request)
        .map(SkillRequest::chatContext)
        .orElse(List.of()).stream()
        .map(message -> new CustomerMessageSentEvent.ChatMessage(
            message.get("role"),
            firstNonBlank(message.get("text"), message.get("content")),
            message.get("timestamp")))
        .toList();
    if (!storedMessages.isEmpty()) {
      return storedMessages;
    }
    if (request.rawMessages() != null && !request.rawMessages().isEmpty()) {
      return request.rawMessages().stream()
          .filter(java.util.Objects::nonNull)
          .map(message -> new CustomerMessageSentEvent.ChatMessage(message.role(), message.text(), message.timestamp()))
          .toList();
    }
    return List.of();
  }

  private String regenerateWarning(int count) {
    int maxCount = skillConfigProvider.get().regenerateMaxCount();
    if (maxCount <= 0 || count < maxCount) {
      return null;
    }
    return "已连续换 " + maxCount + " 次，可以尝试求助组长";
  }

  private Customer firstCustomer(MatchResult match) {
    if (match == null || match.customers() == null || match.customers().isEmpty()) {
      return null;
    }
    return customerQueryService.getByPhone(match.customers().get(0).phone());
  }

  private String buildClientMessage(ChatRecognizeRequest request, RecognitionResult recognized) {
    if (!blank(request.textMessage())) {
      return request.textMessage();
    }
    if (recognized == null || recognized.messages() == null) {
      return "";
    }
    return recognized.messages().stream().map(Message::text).reduce("", (left, right) -> left + "\n" + right).trim();
  }

  private List<Map<String, String>> messages(ChatRecognizeRequest request, RecognitionResult recognized) {
    if (request.rawMessages() != null && !request.rawMessages().isEmpty()) {
      return request.rawMessages().stream().map(m -> messageMap(m.role(), m.text(), m.timestamp())).toList();
    }
    if (recognized == null || recognized.messages() == null) {
      return List.of();
    }
    return recognized.messages().stream().map(m -> messageMap(m.role(), m.text(), null)).toList();
  }

  private Map<String, String> messageMap(String role, String text, String timestamp) {
    return Map.of(
        "role", nvl(role),
        "text", nvl(text),
        "timestamp", nvl(timestamp));
  }

  private Map<String, Object> customerMap(Customer customer) {
    if (customer == null) {
      return Map.of();
    }
    return Map.of(
        "phone", nvl(customer.getPhone()),
        "nickname", nvl(customer.getNickname()),
        "leadType", nvl(customer.getLeadType()),
        "customerStage", nvl(customer.getCustomerStage()),
        "followupNotes", nvl(customer.getFollowupNotes()));
  }

  private String firstNonBlank(String first, String second) {
    return blank(first) ? second : first;
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String nvl(String value) {
    return value == null ? "" : value;
  }

  private record GeneratedReplies(SkillResponse skill, ChatReplySource source) {
  }
}
