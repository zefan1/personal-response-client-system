package com.privateflow.modules.api.chat;

import com.privateflow.common.events.CustomerMessageSentEvent;
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
import com.privateflow.modules.llm.LlmFollowupSuggestionInput;
import com.privateflow.modules.llm.LlmFollowupSuggestionService;
import com.privateflow.modules.llm.LlmSummaryInput;
import com.privateflow.modules.llm.LlmSummaryService;
import com.privateflow.modules.match.MatchRequest;
import com.privateflow.modules.match.MatchResult;
import com.privateflow.modules.match.MatchType;
import com.privateflow.modules.profile.service.FollowupConfirmationService;
import com.privateflow.modules.match.CustomerMatchService;
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
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
  private final LlmFollowupSuggestionService llmFollowupSuggestionService;
  private final LlmSummaryService llmSummaryService;
  private final FollowupConfirmationService followupConfirmationService;
  private final PendingReplyTaskService pendingReplyTaskService;
  private final SupervisionEventService supervisionEventService;

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
      LlmFollowupSuggestionService llmFollowupSuggestionService,
      LlmSummaryService llmSummaryService,
      FollowupConfirmationService followupConfirmationService,
      PendingReplyTaskService pendingReplyTaskService,
      SupervisionEventService supervisionEventService) {
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
    this.llmFollowupSuggestionService = llmFollowupSuggestionService;
    this.llmSummaryService = llmSummaryService;
    this.followupConfirmationService = followupConfirmationService;
    this.pendingReplyTaskService = pendingReplyTaskService;
    this.supervisionEventService = supervisionEventService;
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
    String platformIdentifier = recognized == null ? null : recognized.customerIdentifier();
    if ((isNoMatch(match) || match.matchType() == MatchType.MULTIPLE)
        && blank(request.customerIdentifier())
        && !blank(platformIdentifier)
        && !platformIdentifier.equals(nickname)) {
      match = match(platformIdentifier, phone, request.leadType(), request.sourceTable());
      if (!stillActive(stillActive)) {
        return null;
      }
    }
    match = visibleMatch(match);
    if (!stillActive(stillActive)) {
      return null;
    }
    String clientMessage = buildClientMessage(request, recognized);
    List<Map<String, String>> chatContext = messages(request, recognized);
    if (match.matchType() == MatchType.MULTIPLE) {
      if (!stillActive(stillActive)) {
        return null;
      }
      PendingReplyTaskView pendingTask = pendingReplyTaskService.createWaitingTask(new PendingReplyTaskDraft(
          firstNonBlank(request.replySessionId(), "reply-" + UUID.randomUUID()),
          AuthContext.username(),
          recognized == null ? null : recognized.nickname(),
          phone,
          platformIdentifier,
          request.leadType(),
          request.sourceTable(),
          clientMessage,
          chatContext,
          match.customers()));
      return new ChatResponse(null, nickname, false, match, null, null, null, pendingTask);
    }
    if (!stillActive(stillActive)) {
      return null;
    }
    Customer customer = firstCustomer(match);
    if (customer != null && !customerAccessService.canAccess(customer)) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "no access to this customer");
    }
    if (!stillActive(stillActive)) {
      return null;
    }
    if (customer != null) {
      recordSupervision(
          () -> supervisionEventService.recordPendingEntered(customer, null),
          "pending entered");
    }
    if (!stillActive(stillActive)) {
      return null;
    }
    GeneratedReplies generated = generateSkill(Scene.CHAT_RECOGNIZE, request.leadType(), customer, phone, clientMessage, List.of(), chatContext);
    if (!stillActive(stillActive)) {
      return null;
    }
    String responsePhone = customer == null ? phone : customer.getPhone();
    saveContext(responsePhone, generated, 0);
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
    return new ChatResponse(responsePhone, nickname, match.matchType() == MatchType.NONE, match, generated.skill(), null, generated.source());
  }

  public ChatResponse confirmPendingReplyTask(String taskId, PendingReplyTaskSelectRequest request) {
    if (request == null || blank(request.phone())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "phone is required");
    }
    PendingReplyTask task = pendingReplyTaskService.claimForGeneration(
        taskId,
        AuthContext.username(),
        request.phone());
    return generatePendingReplyTask(task, true);
  }

  public List<PendingReplyTaskView> listPendingReplyTasks() {
    return pendingReplyTaskService.listRecoverable(AuthContext.username());
  }

  public PendingReplyTaskView getPendingReplyTask(String taskId) {
    return pendingReplyTaskService.getRecoverable(taskId, AuthContext.username());
  }

  public ChatResponse retryPendingReplyTask(String taskId) {
    PendingReplyTask task = pendingReplyTaskService.claimRetry(taskId, AuthContext.username());
    return generatePendingReplyTask(task, false);
  }

  public PendingReplyTaskView cancelPendingReplyTask(String taskId) {
    return pendingReplyTaskService.cancel(taskId, AuthContext.username());
  }

  private ChatResponse generatePendingReplyTask(PendingReplyTask task, boolean customerSelectedNow) {
    pendingReplyTaskService.beginGeneration(task.taskId());
    try {
      String selectedPhone = task.selectedPhone();
      Customer customer;
      try {
        customer = customerQueryService.getByPhone(selectedPhone);
      } catch (RuntimeException ex) {
        markPendingTaskFailed(task, ex);
        throw ex;
      }
      if (customer == null) {
        pendingReplyTaskService.releaseSelection(task);
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, "customer not found");
      }
      boolean canAccess;
      try {
        canAccess = customerAccessService.canAccess(customer);
      } catch (RuntimeException ex) {
        markPendingTaskFailed(task, ex);
        throw ex;
      }
      if (!canAccess) {
        pendingReplyTaskService.releaseSelection(task);
        throw new ApiException(ApiErrorCodes.FORBIDDEN, "无权操作该客户");
      }
      if (customerSelectedNow) {
        recordSupervision(
            () -> supervisionEventService.recordPendingEntered(customer, task.taskId()),
            "pending entered");
        recordSupervision(() -> supervisionEventService.recordCustomerSelected(
            customer,
            task.taskId(),
            task.replySessionId()), "customer selected");
      }
      try {
        GeneratedReplies generated = generateSkill(
            Scene.CHAT_RECOGNIZE,
            task.leadType(),
            customer,
            customer.getPhone(),
            task.clientMessage(),
            List.of(),
            task.chatContext() == null ? List.of() : task.chatContext());
        saveContext(customer.getPhone(), generated, 0);
        ChatResponse response = new ChatResponse(
            customer.getPhone(),
            customer.getNickname(),
            false,
            null,
            generated.skill(),
            null,
            generated.source());
        pendingReplyTaskService.markReady(task, response);
        recordSupervision(() -> supervisionEventService.recordGeneratedReply(
            customer,
            Scene.CHAT_RECOGNIZE.name(),
            task.taskId(),
            task.replySessionId(),
            generated.source(),
            generated.skill()), "reply generated");
        auditLogger.log("CALL_SKILL", AuthContext.username(), "CHAT", customer.getPhone(), "pending reply task confirmed");
        return response;
      } catch (RuntimeException ex) {
        markPendingTaskFailed(task, ex);
        throw ex;
      }
    } finally {
      pendingReplyTaskService.endGeneration(task.taskId());
    }
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
    saveContext(customer.getPhone(), generated, 0);
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
    if (request == null || blank(request.phone())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "phone is required");
    }
    RequestContext context = contextStore.read(AuthContext.username(), request.phone()).orElse(null);
    if (context == null) {
      return generate(new GenerateRequest(request.phone(), "ACTIVE_REPLY", null));
    }
    SkillRequest previous = context.request();
    Customer latest = customerQueryService.getByPhone(request.phone());
    if (latest == null) {
      return generate(new GenerateRequest(request.phone(), "ACTIVE_REPLY", null));
    }
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
    contextStore.save(AuthContext.username(), request.phone(), new RequestContext(generated.request(), generated.skill(), count));
    recordSupervision(() -> supervisionEventService.recordGeneratedReply(
        latest,
        Scene.REGENERATE.name(),
        null,
        null,
        generated.source(),
        generated.skill()), "reply generated");
    String warning = regenerateWarning(count);
    return new ChatResponse(request.phone(), null, false, null, generated.skill(), warning, generated.source());
  }

  public Map<String, Object> sendConfirm(SendConfirmRequest request) {
    if (request == null || blank(request.phone()) || blank(request.sentText())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "phone and sentText are required");
    }
    Customer customer = requireSendConfirmAccess(request);
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
    if (customer != null) {
      followupConfirmationService.record(
          customer,
          conversationSummary,
          request.sentText(),
          followupSuggest,
          request.completeCurrentFollowup());
    }
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
        request.completeCurrentFollowup(),
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

  private Customer requireSendConfirmAccess(SendConfirmRequest request) {
    Customer customer = customerQueryService.getByPhone(request.phone());
    if (customer != null) {
      if (!customerAccessService.canAccess(customer)) {
        throw new ApiException(ApiErrorCodes.FORBIDDEN, "无权操作该客户");
      }
      return customer;
    }
    if (!request.isNewCustomer()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "客户不存在");
    }
    if (contextStore.read(AuthContext.username(), request.phone()).isEmpty()) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "无权创建该客户记录");
    }
    return null;
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

  private boolean canAccessCandidate(CustomerSummary candidate) {
    Customer customer = customerQueryService.getByPhone(candidate.phone());
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

  private void saveContext(String phone, GeneratedReplies generated, int regenerateCount) {
    if (!blank(phone)) {
      contextStore.save(
          AuthContext.username(),
          phone,
          new RequestContext(generated.request(), generated.skill(), regenerateCount));
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

  private boolean stillActive(BooleanSupplier stillActive) {
    return stillActive != null && stillActive.getAsBoolean();
  }

  private String pendingTaskErrorCode(RuntimeException ex) {
    if (ex instanceof ApiException apiException && !blank(apiException.getErrorCode())) {
      return apiException.getErrorCode();
    }
    return ApiErrorCodes.INTERNAL_ERROR;
  }

  private void markPendingTaskFailed(PendingReplyTask task, RuntimeException originalFailure) {
    try {
      pendingReplyTaskService.markFailed(task, pendingTaskErrorCode(originalFailure));
    } catch (RuntimeException markFailedFailure) {
      originalFailure.addSuppressed(markFailedFailure);
      log.warn("Could not mark pending reply task {} as failed", task.taskId(), markFailedFailure);
    }
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
