package com.privateflow.modules.api.chat;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.common.events.RecognizedConversationEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.image.ImageRecognitionException;
import com.privateflow.modules.image.ImageRecognitionService;
import com.privateflow.modules.image.Message;
import com.privateflow.modules.image.RecognitionResult;
import com.privateflow.modules.image.Source;
import com.privateflow.modules.llm.LlmReplyGenerationService;
import com.privateflow.modules.llm.FollowupAnalysisPayload;
import com.privateflow.modules.llm.LlmFollowupAnalysisInput;
import com.privateflow.modules.llm.LlmFollowupAnalysisService;
import com.privateflow.modules.llm.FollowupAnalysisRetryService;
import com.privateflow.modules.match.MatchRequest;
import com.privateflow.modules.match.MatchResult;
import com.privateflow.modules.match.MatchType;
import com.privateflow.modules.match.Confidence;
import com.privateflow.modules.profile.service.FollowupConfirmationService;
import com.privateflow.modules.profile.service.FollowupAnalysisFieldMerger;
import com.privateflow.modules.match.CustomerMatchService;
import com.privateflow.modules.match.CustomerMatchException;
import com.privateflow.modules.match.CustomerMatchErrorCodes;
import com.privateflow.modules.match.CustomerSummary;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillGatewayService;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.ReplyTagSnapshot;
import com.privateflow.modules.skill.Suggestion;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import com.privateflow.modules.supervision.SupervisionEventService;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class ChatOrchestrationService {

  private static final Logger log = LoggerFactory.getLogger(ChatOrchestrationService.class);
  private static final int FALLBACK_CONVERSATION_SUMMARY_MAX_CHARS = 2000;

  private final ImageRecognitionService imageRecognitionService;
  private final CustomerMatchService customerMatchService;
  private final SkillGatewayService skillGatewayService;
  private final CustomerQueryService customerQueryService;
  private final CustomerAccessService customerAccessService;
  private final ReplyTagSnapshotBuilder replyTagSnapshotBuilder;
  private final RequestContextStore contextStore;
  private final ApplicationEventPublisher eventPublisher;
  private final AuditLogger auditLogger;
  private final SkillConfigProvider skillConfigProvider;
  private final LlmReplyGenerationService llmReplyGenerationService;
  private final LlmFollowupAnalysisService llmFollowupAnalysisService;
  private final FollowupAnalysisFieldMerger followupAnalysisFieldMerger;
  private final FollowupAnalysisRetryService followupAnalysisRetryService;
  private final FollowupConfirmationService followupConfirmationService;
  private final SupervisionEventService supervisionEventService;
  private final SendConfirmationRepository sendConfirmationRepository;
  private final RecognitionCommunicationArchiveService communicationArchiveService;
  private final Executor apiOrchestrationExecutor;

  @Autowired
  public ChatOrchestrationService(
      ImageRecognitionService imageRecognitionService,
      CustomerMatchService customerMatchService,
      SkillGatewayService skillGatewayService,
      CustomerQueryService customerQueryService,
      CustomerAccessService customerAccessService,
      ReplyTagSnapshotBuilder replyTagSnapshotBuilder,
      RequestContextStore contextStore,
      ApplicationEventPublisher eventPublisher,
      AuditLogger auditLogger,
      SkillConfigProvider skillConfigProvider,
      LlmReplyGenerationService llmReplyGenerationService,
      LlmFollowupAnalysisService llmFollowupAnalysisService,
      FollowupAnalysisFieldMerger followupAnalysisFieldMerger,
      FollowupAnalysisRetryService followupAnalysisRetryService,
      FollowupConfirmationService followupConfirmationService,
      SupervisionEventService supervisionEventService,
      SendConfirmationRepository sendConfirmationRepository,
      RecognitionCommunicationArchiveService communicationArchiveService,
      @Qualifier("apiOrchestrationExecutor") Executor apiOrchestrationExecutor) {
    this.imageRecognitionService = imageRecognitionService;
    this.customerMatchService = customerMatchService;
    this.skillGatewayService = skillGatewayService;
    this.customerQueryService = customerQueryService;
    this.customerAccessService = customerAccessService;
    this.replyTagSnapshotBuilder = replyTagSnapshotBuilder;
    this.contextStore = contextStore;
    this.eventPublisher = eventPublisher;
    this.auditLogger = auditLogger;
    this.skillConfigProvider = skillConfigProvider;
    this.llmReplyGenerationService = llmReplyGenerationService;
    this.llmFollowupAnalysisService = llmFollowupAnalysisService;
    this.followupAnalysisFieldMerger = followupAnalysisFieldMerger;
    this.followupAnalysisRetryService = followupAnalysisRetryService;
    this.followupConfirmationService = followupConfirmationService;
    this.supervisionEventService = supervisionEventService;
    this.sendConfirmationRepository = sendConfirmationRepository;
    this.communicationArchiveService = communicationArchiveService;
    this.apiOrchestrationExecutor = apiOrchestrationExecutor;
  }

  /** Compatibility constructor for focused unit tests that do not need async dispatch. */
  ChatOrchestrationService(
      ImageRecognitionService imageRecognitionService,
      CustomerMatchService customerMatchService,
      SkillGatewayService skillGatewayService,
      CustomerQueryService customerQueryService,
      CustomerAccessService customerAccessService,
      ReplyTagSnapshotBuilder replyTagSnapshotBuilder,
      RequestContextStore contextStore,
      ApplicationEventPublisher eventPublisher,
      AuditLogger auditLogger,
      SkillConfigProvider skillConfigProvider,
      LlmReplyGenerationService llmReplyGenerationService,
      LlmFollowupAnalysisService llmFollowupAnalysisService,
      FollowupAnalysisFieldMerger followupAnalysisFieldMerger,
      FollowupAnalysisRetryService followupAnalysisRetryService,
      FollowupConfirmationService followupConfirmationService,
      SupervisionEventService supervisionEventService,
      SendConfirmationRepository sendConfirmationRepository,
      RecognitionCommunicationArchiveService communicationArchiveService) {
    this(
        imageRecognitionService,
        customerMatchService,
        skillGatewayService,
        customerQueryService,
        customerAccessService,
        replyTagSnapshotBuilder,
        contextStore,
        eventPublisher,
        auditLogger,
        skillConfigProvider,
        llmReplyGenerationService,
        llmFollowupAnalysisService,
        followupAnalysisFieldMerger,
        followupAnalysisRetryService,
        followupConfirmationService,
        supervisionEventService,
        sendConfirmationRepository,
        communicationArchiveService,
        Runnable::run);
  }

  public ChatResponse recognize(ChatRecognizeRequest request) {
    validateRecognitionRequest(request, request == null ? null : request.imageBase64());
    RecognitionResult recognized;
    try {
      recognized = recognizeImage(request.imageBase64());
    } catch (ApiException ex) {
      recordSupervision(() -> supervisionEventService.recordRecognitionFailed(
          request.leadType(),
          request.sourceTable(),
          request.replySessionId(),
          ex.getErrorCode()), "recognition failure");
      throw ex;
    }
    return recognizeResolvedConversation(request, recognized);
  }

  /**
   * Executes a queued screenshot job under the authenticated employee captured at submission time.
   * The image remains an in-memory byte array after the temporary store has read it; it is never
   * serialized into a chat task, event, or audit record.
   */
  ChatResponse recognizeForJob(ChatRecognizeRequest request, byte[] jpegBytes, AuthUser employee) {
    return recognizeForJob(request, jpegBytes, employee, () -> true);
  }

  ChatResponse recognizeForJob(
      ChatRecognizeRequest request,
      byte[] jpegBytes,
      AuthUser employee,
      BooleanSupplier stillActive) {
    validateRecognitionRequest(request, jpegBytes);
    if (employee == null || blank(employee.username())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "recognition employee is required");
    }
    AuthUser previous = AuthContext.current();
    try {
      AuthContext.set(employee);
      RecognitionResult recognized = recognizeImage(jpegBytes);
      if (stillActive == null || !stillActive.getAsBoolean()) {
        return null;
      }
      return recognizeResolvedConversation(request, recognized, stillActive);
    } catch (ApiException ex) {
      if (!stillActive(stillActive)) {
        return null;
      }
      recordSupervision(() -> supervisionEventService.recordRecognitionFailed(
          request.leadType(),
          request.sourceTable(),
          request.replySessionId(),
          ex.getErrorCode()), "recognition failure");
      throw ex;
    } finally {
      if (previous == null) {
        AuthContext.clear();
      } else {
        AuthContext.set(previous);
      }
    }
  }

  private ChatResponse recognizeResolvedConversation(
      ChatRecognizeRequest request, RecognitionResult recognized) {
    return recognizeResolvedConversation(request, recognized, () -> true);
  }

  private ChatResponse recognizeResolvedConversation(
      ChatRecognizeRequest request, RecognitionResult recognized, BooleanSupplier stillActive) {
    if (!stillActive(stillActive)) {
      return null;
    }
    String nickname = firstNonBlank(request.customerIdentifier(), recognized == null ? null : recognized.nickname());
    String phone = recognized == null ? null : recognized.phone();
    MatchResult match = match(nickname, phone, request.leadType(), request.sourceTable());
    if (!stillActive(stillActive)) {
      return null;
    }
    match = visibleMatch(match);
    if (!stillActive(stillActive)) {
      return null;
    }
    String clientMessage = buildClientMessage(request, recognized);
    List<Map<String, String>> chatContext = messages(request, recognized);
    if (!stillActive(stillActive)) {
      return null;
    }
    if (match.matchType() == MatchType.MULTIPLE) {
      return new ChatResponse(
          null,
          nickname,
          false,
          match,
          null,
          "识别到多个相似客户，请先选择对应档案",
          null,
          null,
          recognized,
          true);
    }
    Customer customer = match.matchType() == MatchType.EXACT || match.matchType() == MatchType.FUZZY
        ? firstCustomer(match)
        : null;
    if (customer == null) {
      customer = communicationArchiveService.createRecognitionCustomer(request, recognized);
    }
    return completeRecognizedConversation(
        request, recognized, match, customer, phone, clientMessage, chatContext, stillActive);
  }

  public ChatResponse resolveSelectedCustomer(
      ChatRecognizeRequest request,
      RecognitionResult recognized,
      MatchResult match,
      Long customerId) {
    if (match == null || match.matchType() != MatchType.MULTIPLE || customerId == null || customerId <= 0) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "a selected matching customer is required");
    }
    boolean offered = match.customers() != null && match.customers().stream()
        .anyMatch(candidate -> candidate != null && customerId.equals(candidate.customerId()));
    if (!offered) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "selected customer is not a recognition candidate");
    }
    Customer selected = customerQueryService.getById(customerId);
    if (selected == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "selected customer no longer exists");
    }
    String clientMessage = buildClientMessage(request, recognized);
    return completeRecognizedConversation(
        request,
        recognized,
        match,
        selected,
        recognized == null ? null : recognized.phone(),
        clientMessage,
        messages(request, recognized),
        () -> true);
  }

  private ChatResponse completeRecognizedConversation(
      ChatRecognizeRequest request,
      RecognitionResult recognized,
      MatchResult match,
      Customer customer,
      String recognizedPhone,
      String clientMessage,
      List<Map<String, String>> chatContext,
      BooleanSupplier stillActive) {
    if (customer == null) {
      throw new IllegalStateException("recognition did not resolve a customer");
    }
    if (!customerAccessService.canAccess(customer)) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "no access to this customer");
    }
    if (!stillActive(stillActive)) {
      return null;
    }
    communicationArchiveService.archive(request, recognized, customer, AuthContext.username());
    eventPublisher.publishEvent(new RecognizedConversationEvent(
        customer.getId(), customer.getPhone(),
        chatMessages(request, recognized), AuthContext.username()));
    if (!stillActive(stillActive)) {
      return null;
    }
    recordSupervision(
        () -> supervisionEventService.recordRecognitionProcessed(customer, null),
        "recognition processed");
    if (!stillActive(stillActive)) {
      return null;
    }
    GeneratedReplies generated = generateSkill(
        Scene.CHAT_RECOGNIZE,
        request.leadType(),
        customer,
        recognizedPhone,
        clientMessage,
        List.of(),
        chatContext);
    if (!stillActive(stillActive)) {
      return null;
    }
    String responsePhone = customer.getPhone();
    saveContext(customer, generated, 0);
    if (!stillActive(stillActive)) {
      return null;
    }
    recordSupervision(() -> supervisionEventService.recordGeneratedReply(
        customer,
        Scene.CHAT_RECOGNIZE.name(),
        null,
        request.replySessionId(),
        generated.source(),
        generated.skill()), "reply generated");
    if (!stillActive(stillActive)) {
      return null;
    }
    auditLogger.log("CALL_SKILL", AuthContext.username(), "CHAT", responsePhone, "chat recognize");
    return new ChatResponse(
        responsePhone,
        customer.getNickname(),
        false,
        match,
        generated.skill(),
        null,
        generated.source(),
        customer.getId(),
        recognized,
        false);
  }

  public ChatResponse generate(GenerateRequest request) {
    if (request == null || (request.customerId() == null && blank(request.phone()))) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "customerId or phone is required");
    }
    Customer customer = request.customerId() != null && request.customerId() > 0
        ? customerQueryService.getById(request.customerId())
        : customerQueryService.getByPhone(request.phone());
    if (customer == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "customer not found");
    }
    Scene scene = "OPENING".equalsIgnoreCase(request.scene()) ? Scene.OPENING : Scene.ACTIVE_REPLY;
    String clientMessage = blank(request.clientMessage()) ? customer.getFollowupNotes() : request.clientMessage();
    GeneratedReplies generated = generateSkill(scene, customer.getLeadType(), customer, customer.getPhone(), clientMessage, List.of(), List.of());
    saveContext(customer, generated, 0);
    recordSupervision(() -> supervisionEventService.recordGeneratedReply(
        customer,
        scene.name(),
        null,
        null,
        generated.source(),
        generated.skill()), "reply generated");
    return new ChatResponse(customer.getPhone(), customer.getNickname(), false, null, generated.skill(), null, generated.source());
  }

  public ChatResponse regenerate(RegenerateRequest request) {
    if (request == null || (request.customerId() == null && blank(request.phone()))) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "customerId or phone is required");
    }
    Customer latest = request.customerId() != null && request.customerId() > 0
        ? customerQueryService.getById(request.customerId())
        : customerQueryService.getByPhone(request.phone());
    if (latest == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "customer not found");
    }
    if (!customerAccessService.canAccess(latest)) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "no access to this customer");
    }
    RequestContext context = request.customerId() != null && request.customerId() > 0
        ? contextStore.read(AuthContext.username(), request.customerId()).orElse(null)
        : contextStore.read(AuthContext.username(), request.phone()).orElse(null);
    if (context == null) {
      GeneratedReplies generated = generateSkill(
          Scene.ACTIVE_REPLY,
          latest.getLeadType(),
          latest,
          latest.getPhone(),
          latest.getFollowupNotes(),
          List.of(),
          List.of());
      saveContext(latest, generated, 0);
      return new ChatResponse(latest.getPhone(), latest.getNickname(), false, null, generated.skill(), null, generated.source(), latest.getId(), null, false);
    }
    SkillRequest previous = context.request();
    List<String> previousSuggestions = context.response() == null || context.response().suggestions() == null
        ? List.of()
        : context.response().suggestions().stream().map(s -> s.text()).toList();
    SkillRequest next = new SkillRequest(
        Scene.REGENERATE,
        previous.leadType(),
        latest.getPhone(),
        previous.clientMessage(),
        customerMap(latest),
        previous.systemPrompt(),
        previousSuggestions,
        previous.chatContext(),
        AuthContext.username(),
        loadReplyTags(latest));
    GeneratedReplies generated = generateReplies(next);
    int count = context.regenerateCount() + 1;
    saveContext(latest, generated, count);
    recordSupervision(() -> supervisionEventService.recordGeneratedReply(
        latest,
        Scene.REGENERATE.name(),
        null,
        null,
        generated.source(),
        generated.skill()), "reply generated");
    String warning = regenerateWarning(count);
    return new ChatResponse(latest.getPhone(), latest.getNickname(), false, null, generated.skill(), warning, generated.source(), latest.getId(), null, false);
  }

  @Transactional
  public Map<String, Object> registerPendingSend(PendingSendRequest request) {
    if (request == null || blank(request.confirmationId()) || blank(request.copiedText())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "confirmationId and copiedText are required");
    }
    if (request.copiedText().trim().length() > 4000) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "copiedText exceeds 4000 characters");
    }
    sendConfirmationRepository.registerPending(request, AuthContext.username());
    return Map.of("accepted", true);
  }

  @Transactional
  public Map<String, Object> updatePendingSend(PendingSendStatusRequest request) {
    if (request == null || blank(request.confirmationId()) || blank(request.status())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "confirmationId and status are required");
    }
    String status = request.status().trim().toUpperCase(java.util.Locale.ROOT);
    if (!status.equals("AWAITING_DECISION")
        && !status.equals("UNSENT")
        && !status.equals("RECOGNITION_RETRY")) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "pending send status is invalid");
    }
    int reminderCount = request.reminderCount() == null ? 0 : Math.max(0, Math.min(5, request.reminderCount()));
    sendConfirmationRepository.updatePendingStatus(
        request.confirmationId(), AuthContext.username(), status, reminderCount);
    return Map.of("accepted", true, "status", status);
  }

  @Transactional
  public Map<String, Object> sendConfirm(SendConfirmRequest request) {
    if (request == null || blank(request.sentText())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "sentText is required");
    }
    Customer customer = resolveSendConfirmCustomer(request);
    if (!blank(request.confirmationId())
        && !sendConfirmationRepository.claim(
            request.confirmationId().trim(),
            AuthContext.username(),
            String.valueOf(customer.getId()))) {
      return Map.of("accepted", true, "duplicate", true);
    }
    if (!blank(request.confirmationId())) {
      sendConfirmationRepository.claimPendingForSend(request.confirmationId(), AuthContext.username());
    }
    String operator = AuthContext.username();
    communicationArchiveService.archiveConfirmedEmployeeMessage(customer, request.sentText(), operator);
    if (!blank(request.confirmationId())) {
      // The button should acknowledge immediately and remain idempotent while background work runs.
      sendConfirmationRepository.markPendingSent(request.confirmationId(), operator, customer.getId());
    }
    apiOrchestrationExecutor.execute(() -> processConfirmedSend(request, customer, operator));
    return Map.of("accepted", true, "duplicate", false);
  }

  private void processConfirmedSend(SendConfirmRequest request, Customer customer, String operator) {
    try {
      List<CustomerMessageSentEvent.ChatMessage> rawMessages = sendConfirmMessages(request, operator);
      Customer analysisCustomer = customer == null ? customerFrom(request) : customer;
      FollowupAnalysisPayload analysis = llmFollowupAnalysisService.tryAnalyze(new LlmFollowupAnalysisInput(
          analysisCustomer,
          rawMessages,
          request.sentText(),
          request.selectedDirection(),
          operator)).orElse(null);
      Map<String, Object> followupFields = analysis == null
          ? requestFollowupFields(request)
          : followupAnalysisFieldMerger.merge(analysisCustomer, analysis);
      if (analysis == null && llmFollowupAnalysisService.enabled() && !blank(customer.getPhone())) {
        followupAnalysisRetryService.enqueue(
            request.confirmationId(),
            customer.getPhone(),
            rawMessages,
            request.sentText(),
            request.selectedDirection(),
            operator);
      }
      String conversationSummary = analysis != null && !blank(analysis.followupRecord())
          ? analysis.followupRecord()
          : nvl(request.conversationSummary());
      CustomerMessageSentEvent.FollowupSuggestPayload followupSuggest = followupSuggest(followupFields);
      followupConfirmationService.recordAnalysis(customer, followupFields, true);
      eventPublisher.publishEvent(new CustomerMessageSentEvent(
          customer.getPhone(),
          customer.getNickname(),
          customer.getSourceRowId() == null || customer.getSourceRowId().isBlank(),
          customer.getSourceTable(),
          customer.getLeadType(),
          conversationSummary,
          rawMessages,
          request.sentText(),
          request.selectedDirection(),
          followupSuggest,
          request.completeCurrentFollowup(),
          followupFields,
          operator,
          customer.getId()));
      auditLogger.log("SEND_CONFIRM", operator, "CUSTOMER", String.valueOf(customer.getId()), "message sent");
    } catch (RuntimeException ex) {
      log.error("confirmed send background processing failed, customerId={}, confirmationId={}",
          customer == null ? null : customer.getId(), request.confirmationId(), ex);
    }
  }

  private Customer customerFrom(SendConfirmRequest request) {
    Customer customer = new Customer();
    customer.setPhone(request.phone());
    customer.setNickname(request.nickname());
    customer.setLeadType(request.leadType());
    customer.setSourceTable(request.sourceTable());
    return customer;
  }

  private Map<String, Object> requestFollowupFields(SendConfirmRequest request) {
    Map<String, Object> fields = new java.util.LinkedHashMap<>();
    if (!blank(request.conversationSummary())) {
      fields.put("followupNotes", request.conversationSummary().trim());
    }
    if (request.followupSuggest() != null && !blank(request.followupSuggest().nextFollowupAt())) {
      fields.put("nextFollowupAt", request.followupSuggest().nextFollowupAt());
      fields.put("nextFollowupDir", request.followupSuggest().nextFollowupDir());
    } else if (request.completeCurrentFollowup()) {
      fields.put("nextFollowupAt", null);
      fields.put("nextFollowupDir", null);
    }
    return fields;
  }

  private CustomerMessageSentEvent.FollowupSuggestPayload followupSuggest(Map<String, Object> fields) {
    String nextAt = asString(fields == null ? null : fields.get("nextFollowupAt"));
    String nextDirection = asString(fields == null ? null : fields.get("nextFollowupDir"));
    if (blank(nextAt) || blank(nextDirection)) {
      return null;
    }
    return new CustomerMessageSentEvent.FollowupSuggestPayload(nextAt, nextDirection);
  }

  private String asString(Object value) {
    return value == null ? null : value.toString();
  }

  private Customer resolveSendConfirmCustomer(SendConfirmRequest request) {
    Customer customer = request.customerId() == null || request.customerId() <= 0
        ? communicationArchiveService.createPendingSendCustomer(request)
        : customerQueryService.getById(request.customerId());
    if (customer != null) {
      if (!customerAccessService.canAccess(customer)) {
        throw new ApiException(ApiErrorCodes.FORBIDDEN, "无权操作该客户");
      }
      return customer;
    }
    throw new ApiException(ApiErrorCodes.BAD_REQUEST, "customer not found");
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
      return recognizeImage(Base64.getDecoder().decode(imageBase64));
    } catch (ApiException ex) {
      throw ex;
    } catch (IllegalArgumentException ex) {
      throw new ApiException("30-10002", "图片格式不支持，请重新截图或使用 PNG/JPG");
    } catch (ImageRecognitionException ex) {
      throw new ApiException(ex.getErrorCode(), ex.getMessage());
    } catch (RuntimeException ex) {
      throw new ApiException("30-10001", "图片识别失败，请使用文字通道后重新生成回复");
    }
  }

  private RecognitionResult recognizeImage(byte[] imageBytes) {
    try {
      return imageRecognitionService.recognize(imageBytes, Source.BUTTON_CLICK);
    } catch (ImageRecognitionException ex) {
      throw new ApiException(ex.getErrorCode(), ex.getMessage());
    } catch (RuntimeException ex) {
      throw new ApiException("30-10001", "图片识别失败，请使用文字通道后重新生成回复");
    }
  }

  private void validateRecognitionRequest(ChatRecognizeRequest request, Object imagePayload) {
    if (request == null || (imagePayload == null && blank(request.textMessage()))) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "please provide screenshot or chat text");
    }
  }

  private MatchResult match(String nickname, String phone, String leadType, String sourceTable) {
    try {
      return customerMatchService.match(new MatchRequest(nickname, phone, leadType, sourceTable, AuthContext.username()));
    } catch (CustomerMatchException ex) {
      throw new ApiException(ex.getErrorCode(), ex.getMessage());
    } catch (RuntimeException ex) {
      log.error("customer matching failed during recognition", ex);
      throw new ApiException(CustomerMatchErrorCodes.MATCH_FAILED, "客户匹配服务暂不可用");
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
        AuthContext.username(),
        loadReplyTags(customer));
    return generateReplies(skillRequest);
  }

  private GeneratedReplies generateReplies(SkillRequest skillRequest) {
    SkillResponse skillGuidance = skillGatewayService.generateReplies(skillRequest);
    ChatReplySource skillSource = replySourceForSkill(skillGuidance);
    if ("FALLBACK".equals(skillSource.source())) {
      return new GeneratedReplies(skillRequest, skillGuidance, skillSource);
    }
    return llmReplyGenerationService.tryGenerate(skillRequest, skillGuidance)
        .map(reply -> new GeneratedReplies(skillRequest, reply, ChatReplySource.llmWithSkill()))
        .orElseGet(() -> guidanceOnly(skillGuidance)
            ? new GeneratedReplies(skillRequest, configuredFallback(), ChatReplySource.fallback("LLM generation failed; using configured fallback"))
            : new GeneratedReplies(skillRequest, skillGuidance, ChatReplySource.skill()));
  }

  private boolean isNoMatch(MatchResult result) {
    return result == null || result.matchType() == MatchType.NONE;
  }

  private MatchResult visibleMatch(MatchResult match) {
    if (match == null) {
      return MatchResult.none();
    }
    if (match.matchType() != MatchType.MULTIPLE || match.customers() == null) {
      return match;
    }
    List<CustomerSummary> accessibleCandidates = match.customers().stream()
        .filter(java.util.Objects::nonNull)
        .filter(this::canAccessCandidate)
        .toList();
    if (accessibleCandidates.isEmpty()) {
      return MatchResult.none();
    }
    if (accessibleCandidates.size() == 1) {
      return new MatchResult(MatchType.FUZZY, accessibleCandidates, 1);
    }
    return new MatchResult(MatchType.MULTIPLE, accessibleCandidates, accessibleCandidates.size());
  }

  private List<CustomerMessageSentEvent.ChatMessage> chatMessages(
      ChatRecognizeRequest request, RecognitionResult recognized) {
    if (request != null && request.rawMessages() != null && !request.rawMessages().isEmpty()) {
      return request.rawMessages().stream()
          .filter(java.util.Objects::nonNull)
          .map(message -> new CustomerMessageSentEvent.ChatMessage(
              message.role(), message.text(), message.timestamp()))
          .toList();
    }
    if (recognized != null && recognized.messages() != null) {
      return recognized.messages().stream()
          .filter(java.util.Objects::nonNull)
          .map(message -> new CustomerMessageSentEvent.ChatMessage(
              message.role(), message.text(), recognized.timestamp()))
          .toList();
    }
    if (request != null && !blank(request.textMessage())) {
      return List.of(new CustomerMessageSentEvent.ChatMessage("client", request.textMessage(), null));
    }
    return List.of();
  }

  private MatchResult rememberedMatch(Customer customer) {
    return new MatchResult(
        MatchType.EXACT,
        List.of(new CustomerSummary(
            customer.getPhone(),
            customer.getPhone(),
            customer.getNickname(),
            customer.getSourceChannel(),
            customer.getLeadType(),
            customer.getAssignedKeeper(),
            customer.getLastFollowupAt(),
            customer.getIntendedStore(),
            Confidence.HIGH,
            customer.getId())),
        1);
  }

  private boolean canAccessCandidate(CustomerSummary candidate) {
    Customer customer = candidate.customerId() == null
        ? customerQueryService.getByPhone(candidate.phoneFull())
        : customerQueryService.getById(candidate.customerId());
    return customer != null && customerAccessService.canAccess(customer);
  }

  private ChatReplySource replySourceForSkill(SkillResponse skill) {
    if (skill != null
        && (skill.suggestions() == null || skill.suggestions().isEmpty())
        && skill.guidance() != null
        && !skill.guidance().isBlank()) {
      return ChatReplySource.skill();
    }
    if (skill == null || skill.suggestions() == null || skill.suggestions().isEmpty()) {
      return ChatReplySource.fallback("Skill 未返回可用回复");
    }
    String direction = skill.suggestions().get(0).direction();
    if ("SYSTEM_FALLBACK".equalsIgnoreCase(direction)) {
      return ChatReplySource.fallback("Skill 不可用，已使用系统降级回复");
    }
    return ChatReplySource.skill();
  }

  private boolean guidanceOnly(SkillResponse skill) {
    return skill != null
        && skill.guidance() != null
        && !skill.guidance().isBlank()
        && (skill.suggestions() == null || skill.suggestions().isEmpty());
  }

  private SkillResponse configuredFallback() {
    return new SkillResponse(
        List.of(new Suggestion(skillConfigProvider.get().fallbackReply(), "SYSTEM_FALLBACK", "")),
        null,
        null,
        null);
  }

  private List<ReplyTagSnapshot> loadReplyTags(Customer customer) {
    if (customer == null || customer.getId() == null) {
      return List.of();
    }
    try {
      return replyTagSnapshotBuilder.build(customer.getId());
    } catch (ApiException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      String phone = customer.getPhone();
      log.warn(
          "reply tag snapshot load failed, phoneLast4={}, reason={}",
          lastFour(phone),
          ex.getMessage());
      auditLogger.log(
          "CUSTOMER_TAGS_READ_DEGRADED",
          AuthContext.username(),
          "CUSTOMER",
          phone,
          clip(ex.getMessage(), 500));
      return List.of();
    }
  }

  private void saveContext(Customer customer, GeneratedReplies generated, int regenerateCount) {
    if (customer != null && customer.getId() != null && customer.getId() > 0) {
      contextStore.save(
          AuthContext.username(),
          customer.getId(),
          new RequestContext(generated.request(), generated.skill(), regenerateCount));
    } else if (customer != null && !blank(customer.getPhone())) {
      contextStore.save(
          AuthContext.username(),
          customer.getPhone(),
          new RequestContext(generated.request(), generated.skill(), regenerateCount));
    }
  }

  private List<CustomerMessageSentEvent.ChatMessage> sendConfirmMessages(SendConfirmRequest request, String operator) {
    Optional<RequestContext> context = request.customerId() != null && request.customerId() > 0
        ? contextStore.read(operator, request.customerId())
        : contextStore.read(operator, request.phone());
    List<CustomerMessageSentEvent.ChatMessage> storedMessages = context
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
    CustomerSummary candidate = match.customers().get(0);
    return candidate.customerId() == null
        ? customerQueryService.getByPhone(candidate.phoneFull())
        : customerQueryService.getById(candidate.customerId());
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

  private boolean stillActive(BooleanSupplier stillActive) {
    return stillActive != null && stillActive.getAsBoolean();
  }

  private void recordSupervision(Runnable record, String event) {
    if (supervisionEventService == null) {
      return;
    }
    try {
      record.run();
    } catch (RuntimeException ex) {
      log.warn("Supervision event recording skipped, event={}", event);
    }
  }

  private String nvl(String value) {
    return value == null ? "" : value;
  }

  private String lastFour(String phone) {
    if (phone == null || phone.isBlank()) {
      return "";
    }
    return phone.length() <= 4 ? phone : phone.substring(phone.length() - 4);
  }

  private String clip(String value, int maxLength) {
    if (value == null) {
      return "";
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private record GeneratedReplies(SkillRequest request, SkillResponse skill, ChatReplySource source) {
  }
}
