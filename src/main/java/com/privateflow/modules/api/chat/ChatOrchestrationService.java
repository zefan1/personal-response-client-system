package com.privateflow.modules.api.chat;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.image.ImageRecognitionService;
import com.privateflow.modules.image.Message;
import com.privateflow.modules.image.RecognitionResult;
import com.privateflow.modules.image.Source;
import com.privateflow.modules.match.MatchRequest;
import com.privateflow.modules.match.MatchResult;
import com.privateflow.modules.match.MatchType;
import com.privateflow.modules.match.CustomerMatchService;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillGatewayService;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.SkillResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class ChatOrchestrationService {

  private final ImageRecognitionService imageRecognitionService;
  private final CustomerMatchService customerMatchService;
  private final SkillGatewayService skillGatewayService;
  private final CustomerQueryService customerQueryService;
  private final RequestContextStore contextStore;
  private final ApplicationEventPublisher eventPublisher;
  private final AuditLogger auditLogger;

  public ChatOrchestrationService(
      ImageRecognitionService imageRecognitionService,
      CustomerMatchService customerMatchService,
      SkillGatewayService skillGatewayService,
      CustomerQueryService customerQueryService,
      RequestContextStore contextStore,
      ApplicationEventPublisher eventPublisher,
      AuditLogger auditLogger) {
    this.imageRecognitionService = imageRecognitionService;
    this.customerMatchService = customerMatchService;
    this.skillGatewayService = skillGatewayService;
    this.customerQueryService = customerQueryService;
    this.contextStore = contextStore;
    this.eventPublisher = eventPublisher;
    this.auditLogger = auditLogger;
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
    SkillResponse skill = generateSkill(Scene.CHAT_RECOGNIZE, request.leadType(), customer, phone, clientMessage, List.of(), messages(request, recognized));
    String responsePhone = customer == null ? phone : customer.getPhone();
    saveContext(responsePhone, clientMessage, skill, Scene.CHAT_RECOGNIZE, customer, request.leadType(), 0);
    auditLogger.log("CALL_SKILL", AuthContext.username(), "CHAT", responsePhone, "chat recognize");
    return new ChatResponse(responsePhone, nickname, match.matchType() == MatchType.NONE, match, skill, null);
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
    SkillResponse skill = generateSkill(scene, customer.getLeadType(), customer, customer.getPhone(), clientMessage, List.of(), List.of());
    saveContext(customer.getPhone(), clientMessage, skill, scene, customer, customer.getLeadType(), 0);
    return new ChatResponse(customer.getPhone(), customer.getNickname(), false, null, skill, null);
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
    SkillResponse skill = skillGatewayService.generateReplies(next);
    int count = context.regenerateCount() + 1;
    contextStore.save(AuthContext.username(), request.phone(), new RequestContext(next, skill, count));
    String warning = count >= 3 ? "已连续换 3 次，可以尝试求助组长" : null;
    return new ChatResponse(request.phone(), null, false, null, skill, warning);
  }

  public Map<String, Object> sendConfirm(SendConfirmRequest request) {
    if (request == null || blank(request.phone()) || blank(request.sentText())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "phone and sentText are required");
    }
    List<CustomerMessageSentEvent.ChatMessage> rawMessages = request.rawMessages() == null ? List.of() : request.rawMessages().stream()
        .map(message -> new CustomerMessageSentEvent.ChatMessage(message.role(), message.text(), message.timestamp()))
        .toList();
    eventPublisher.publishEvent(new CustomerMessageSentEvent(
        request.phone(),
        request.nickname(),
        request.isNewCustomer(),
        request.sourceTable(),
        request.leadType(),
        firstNonBlank(request.conversationSummary(), request.sentText()),
        rawMessages,
        request.sentText(),
        request.selectedDirection(),
        request.followupSuggest(),
        AuthContext.username()));
    auditLogger.log("SEND_CONFIRM", AuthContext.username(), "CUSTOMER", request.phone(), "message sent");
    return Map.of("accepted", true);
  }

  private RecognitionResult recognizeImage(String imageBase64) {
    if (blank(imageBase64)) {
      return null;
    }
    try {
      return imageRecognitionService.recognize(Base64.getDecoder().decode(imageBase64), Source.BUTTON_CLICK);
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private MatchResult match(String nickname, String phone, String leadType, String sourceTable) {
    try {
      return customerMatchService.match(new MatchRequest(nickname, phone, leadType, sourceTable, AuthContext.username()));
    } catch (RuntimeException ex) {
      return MatchResult.none();
    }
  }

  private SkillResponse generateSkill(Scene scene, String leadType, Customer customer, String phone, String clientMessage, List<String> previousSuggestions, List<Map<String, String>> chatContext) {
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
    return skillGatewayService.generateReplies(skillRequest);
  }

  private void saveContext(String phone, String clientMessage, SkillResponse skill, Scene scene, Customer customer, String leadType, int regenerateCount) {
    if (!blank(phone)) {
      SkillRequest skillRequest = new SkillRequest(scene, leadType, phone, clientMessage, customerMap(customer), Map.of(), List.of(), List.of(), AuthContext.username());
      contextStore.save(AuthContext.username(), phone, new RequestContext(skillRequest, skill, regenerateCount));
    }
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
      return request.rawMessages().stream().map(m -> Map.of("role", nvl(m.role()), "text", nvl(m.text()))).toList();
    }
    if (recognized == null || recognized.messages() == null) {
      return List.of();
    }
    return recognized.messages().stream().map(m -> Map.of("role", nvl(m.role()), "text", nvl(m.text()))).toList();
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
}
