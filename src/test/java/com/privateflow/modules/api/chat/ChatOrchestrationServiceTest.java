package com.privateflow.modules.api.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.image.ImageErrorCodes;
import com.privateflow.modules.image.ImageRecognitionException;
import com.privateflow.modules.image.ImageRecognitionService;
import com.privateflow.modules.image.RecognitionResult;
import com.privateflow.modules.llm.LlmReplyGenerationService;
import com.privateflow.modules.llm.LlmFollowupSuggestionInput;
import com.privateflow.modules.llm.LlmFollowupSuggestionService;
import com.privateflow.modules.llm.LlmSummaryInput;
import com.privateflow.modules.llm.LlmSummaryService;
import com.privateflow.modules.match.CustomerMatchService;
import com.privateflow.modules.match.Confidence;
import com.privateflow.modules.match.CustomerSummary;
import com.privateflow.modules.match.MatchRequest;
import com.privateflow.modules.match.MatchResult;
import com.privateflow.modules.match.MatchType;
import com.privateflow.modules.profile.service.FollowupConfirmationService;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillGatewayService;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.ReplyTagSnapshot;
import com.privateflow.modules.skill.Suggestion;
import com.privateflow.modules.skill.config.SkillConfig;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import com.privateflow.modules.supervision.SupervisionEventService;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

class ChatOrchestrationServiceTest {

  private ImageRecognitionService imageRecognitionService;
  private CustomerMatchService customerMatchService;
  private SkillGatewayService skillGatewayService;
  private RequestContextStore contextStore;
  private SkillConfigProvider skillConfigProvider;
  private LlmReplyGenerationService llmReplyGenerationService;
  private LlmFollowupSuggestionService llmFollowupSuggestionService;
  private LlmSummaryService llmSummaryService;
  private ApplicationEventPublisher eventPublisher;
  private AuditLogger auditLogger;
  private CustomerQueryService customerQueryService;
  private CustomerAccessService customerAccessService;
  private ReplyTagSnapshotBuilder replyTagSnapshotBuilder;
  private FollowupConfirmationService followupConfirmationService;
  private PendingReplyTaskService pendingReplyTaskService;
  private SupervisionEventService supervisionEventService;
  private Customer accessibleCustomer;
  private ChatOrchestrationService service;

  @BeforeEach
  void setUp() {
    AuthContext.set(new AuthUser("keeper-1", "管家", Role.KEEPER, null));
    imageRecognitionService = org.mockito.Mockito.mock(ImageRecognitionService.class);
    customerMatchService = org.mockito.Mockito.mock(CustomerMatchService.class);
    skillGatewayService = org.mockito.Mockito.mock(SkillGatewayService.class);
    contextStore = org.mockito.Mockito.mock(RequestContextStore.class);
    skillConfigProvider = org.mockito.Mockito.mock(SkillConfigProvider.class);
    llmReplyGenerationService = org.mockito.Mockito.mock(LlmReplyGenerationService.class);
    llmFollowupSuggestionService = org.mockito.Mockito.mock(LlmFollowupSuggestionService.class);
    llmSummaryService = org.mockito.Mockito.mock(LlmSummaryService.class);
    eventPublisher = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    auditLogger = org.mockito.Mockito.mock(AuditLogger.class);
    customerQueryService = org.mockito.Mockito.mock(CustomerQueryService.class);
    customerAccessService = org.mockito.Mockito.mock(CustomerAccessService.class);
    replyTagSnapshotBuilder = org.mockito.Mockito.mock(ReplyTagSnapshotBuilder.class);
    followupConfirmationService = org.mockito.Mockito.mock(FollowupConfirmationService.class);
    pendingReplyTaskService = org.mockito.Mockito.mock(PendingReplyTaskService.class);
    supervisionEventService = org.mockito.Mockito.mock(SupervisionEventService.class);
    when(skillConfigProvider.get()).thenReturn(skillConfig(3));
    when(skillGatewayService.generateReplies(any())).thenReturn(new SkillResponse(
        List.of(new Suggestion("skill guidance", "NEXT_STEP", "fixed workflow")),
        null,
        null,
        null));
    when(llmReplyGenerationService.tryGenerate(any(), any())).thenReturn(Optional.empty());
    when(llmFollowupSuggestionService.trySuggest(any())).thenReturn(Optional.empty());
    when(llmSummaryService.trySummarize(any())).thenReturn(Optional.empty());
    accessibleCustomer = customer("18800001111");
    when(customerQueryService.getByPhone("18800001111")).thenReturn(accessibleCustomer);
    when(customerAccessService.canAccess(accessibleCustomer)).thenReturn(true);
    service = new ChatOrchestrationService(
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
        llmFollowupSuggestionService,
        llmSummaryService,
        followupConfirmationService,
        pendingReplyTaskService,
        supervisionEventService);
  }

  @AfterEach
  void tearDown() {
    AuthContext.clear();
  }

  @Test
  void recognizeReturnsImageErrorInsteadOfContinuingWithEmptyContext() {
    when(imageRecognitionService.recognize(any(), any())).thenThrow(new ImageRecognitionException(
        ImageErrorCodes.IMAGE_RECOGNITION_FAILED,
        "图片识别失败，请使用文字通道后重新生成回复"));

    ApiException exception = assertThrows(ApiException.class, () -> service.recognize(new ChatRecognizeRequest(
        Base64.getEncoder().encodeToString("bad-image".getBytes()),
        null,
        null,
        null,
        null,
        null)));

    assertEquals(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, exception.getErrorCode());
    verify(customerMatchService, never()).match(any());
    verify(skillGatewayService, never()).generateReplies(any());
    verify(supervisionEventService).recordRecognitionFailed(
        null,
        null,
        null,
        ImageErrorCodes.IMAGE_RECOGNITION_FAILED);
  }

  @Test
  void recognizeRecordsGeneratedReplyAndPendingEntryForAResolvedCustomer() {
    when(imageRecognitionService.recognize(any(), any())).thenReturn(new RecognitionResult(
        "Alice",
        "18800001111",
        List.of(new com.privateflow.modules.image.Message("client", "hello")),
        "12:00"));
    when(customerMatchService.match(any())).thenReturn(new MatchResult(
        MatchType.EXACT,
        List.of(new CustomerSummary(
            "18800001111", "18800001111", "Alice", "WECHAT", "TUAN_GOU", "keeper-1", null, null, Confidence.HIGH)),
        1));

    service.recognize(new ChatRecognizeRequest(
        Base64.getEncoder().encodeToString("image".getBytes()),
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        List.of(),
        "reply-100-1"));

    verify(supervisionEventService).recordPendingEntered(accessibleCustomer, null);
    verify(supervisionEventService).recordGeneratedReply(
        accessibleCustomer,
        "CHAT_RECOGNIZE",
        null,
        "reply-100-1",
        ChatReplySource.skill(),
        new SkillResponse(List.of(new Suggestion("skill guidance", "NEXT_STEP", "fixed workflow")), null, null, null));
  }

  @Test
  void recognizeUsesPlatformIdentifierAsSecondaryMatchClue() {
    when(imageRecognitionService.recognize(any(), any())).thenReturn(new RecognitionResult(
        "Displayed name",
        null,
        List.of(new com.privateflow.modules.image.Message("client", "hello")),
        null,
        "douyin_user_88",
        "DOUYIN_WEB",
        0.92));
    when(customerMatchService.match(any()))
        .thenReturn(com.privateflow.modules.match.MatchResult.none())
        .thenReturn(com.privateflow.modules.match.MatchResult.none());

    service.recognize(new ChatRecognizeRequest(
        Base64.getEncoder().encodeToString("image".getBytes()),
        null,
        null,
        "TUAN_GOU",
        "source-table",
        List.of()));

    org.mockito.ArgumentCaptor<MatchRequest> captor = org.mockito.ArgumentCaptor.forClass(MatchRequest.class);
    verify(customerMatchService, org.mockito.Mockito.times(2)).match(captor.capture());
    assertEquals("Displayed name", captor.getAllValues().get(0).nickname());
    assertEquals("douyin_user_88", captor.getAllValues().get(1).nickname());
  }

  @Test
  void recognizeUsesPlatformIdentifierToDisambiguateMultipleNicknameMatches() {
    when(imageRecognitionService.recognize(any(), any())).thenReturn(new RecognitionResult(
        "Displayed name",
        null,
        List.of(new com.privateflow.modules.image.Message("client", "hello")),
        null,
        "douyin_user_88",
        "DOUYIN_WEB",
        0.92));
    when(customerMatchService.match(any()))
        .thenReturn(new com.privateflow.modules.match.MatchResult(
            com.privateflow.modules.match.MatchType.MULTIPLE,
            List.of(),
            2))
        .thenReturn(com.privateflow.modules.match.MatchResult.none());

    service.recognize(new ChatRecognizeRequest(
        Base64.getEncoder().encodeToString("image".getBytes()),
        null,
        null,
        "TUAN_GOU",
        "source-table",
        List.of()));

    org.mockito.ArgumentCaptor<MatchRequest> captor = org.mockito.ArgumentCaptor.forClass(MatchRequest.class);
    verify(customerMatchService, org.mockito.Mockito.times(2)).match(captor.capture());
    assertEquals("Displayed name", captor.getAllValues().get(0).nickname());
    assertEquals("douyin_user_88", captor.getAllValues().get(1).nickname());
  }

  @Test
  void recognizeMultipleCreatesWaitingTaskWithoutCallingSkillOrReplyLlm() {
    when(imageRecognitionService.recognize(any(), any())).thenReturn(new RecognitionResult(
        "同名客户",
        null,
        List.of(new com.privateflow.modules.image.Message("client", "我想了解项目")),
        "12:00"));
    when(customerMatchService.match(any())).thenReturn(new MatchResult(
        MatchType.MULTIPLE,
        List.of(
            new CustomerSummary("18800001111", "18800001111", "同名客户", "WECHAT", "TUAN_GOU", "keeper-1", null, "门店 A", Confidence.HIGH),
            new CustomerSummary("18800002222", "18800002222", "同名客户", "WECHAT", "TUAN_GOU", "keeper-1", null, "门店 B", Confidence.HIGH)),
        2));
    when(pendingReplyTaskService.createWaitingTask(any())).thenReturn(new PendingReplyTaskView(
        "task-1",
        "reply-100-1",
        PendingReplyTaskStatus.WAITING_CUSTOMER,
        List.of(),
        null,
        null,
        null,
        java.time.LocalDateTime.of(2026, 7, 23, 10, 0)));

    ChatResponse response = service.recognize(new ChatRecognizeRequest(
        Base64.getEncoder().encodeToString("image".getBytes()),
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        List.of(),
        "reply-100-1"));

    assertEquals("task-1", response.pendingTask().taskId());
    assertNull(response.skill());
    verify(skillGatewayService, never()).generateReplies(any());
    verify(llmReplyGenerationService, never()).tryGenerate(any(), any());
  }

  @Test
  void listPendingReplyTasksUsesTheAuthenticatedOwner() {
    PendingReplyTaskView waiting = pendingTaskView(PendingReplyTaskStatus.WAITING_CUSTOMER, null);
    when(pendingReplyTaskService.listRecoverable("keeper-1")).thenReturn(List.of(waiting));

    List<PendingReplyTaskView> tasks = service.listPendingReplyTasks();

    assertEquals(List.of(waiting), tasks);
    verify(pendingReplyTaskService).listRecoverable("keeper-1");
  }

  @Test
  void getReadyPendingReplyTaskReturnsTheSavedResponseWithoutGeneratingAgain() {
    ChatResponse savedResponse = new ChatResponse(
        "18800001111",
        "Alice",
        false,
        null,
        new SkillResponse(List.of(new Suggestion("saved reply", "NEXT_STEP", "saved")), null, null, null),
        null,
        ChatReplySource.skill());
    PendingReplyTaskView ready = pendingTaskView(PendingReplyTaskStatus.READY, savedResponse);
    when(pendingReplyTaskService.getRecoverable("task-1", "keeper-1")).thenReturn(ready);

    PendingReplyTaskView result = service.getPendingReplyTask("task-1");

    assertEquals(savedResponse, result.response());
    verify(pendingReplyTaskService).getRecoverable("task-1", "keeper-1");
    verify(skillGatewayService, never()).generateReplies(any());
    verify(llmReplyGenerationService, never()).tryGenerate(any(), any());
  }

  @Test
  void cancelPendingReplyTaskUsesTheAuthenticatedOwner() {
    PendingReplyTaskView cancelled = pendingTaskView(PendingReplyTaskStatus.CANCELLED, null);
    when(pendingReplyTaskService.cancel("task-1", "keeper-1")).thenReturn(cancelled);

    PendingReplyTaskView result = service.cancelPendingReplyTask("task-1");

    assertEquals(cancelled, result);
    verify(pendingReplyTaskService).cancel("task-1", "keeper-1");
  }

  @Test
  void retryPendingReplyTaskGeneratesFromThePreviouslySelectedCustomerAndOriginalChat() {
    PendingReplyTask task = claimedTask();
    when(pendingReplyTaskService.claimRetry("task-1", "keeper-1")).thenReturn(task);

    ChatResponse response = service.retryPendingReplyTask("task-1");

    assertEquals("18800001111", response.phone());
    org.mockito.ArgumentCaptor<SkillRequest> requestCaptor = org.mockito.ArgumentCaptor.forClass(SkillRequest.class);
    verify(skillGatewayService).generateReplies(requestCaptor.capture());
    assertEquals(Scene.CHAT_RECOGNIZE, requestCaptor.getValue().scene());
    assertEquals("18800001111", requestCaptor.getValue().phone());
    assertEquals("I want to know more", requestCaptor.getValue().clientMessage());
    assertEquals("I want to know more", requestCaptor.getValue().chatContext().get(0).get("text"));
    verify(contextStore).save(eq("keeper-1"), eq("18800001111"), any(RequestContext.class));
    verify(pendingReplyTaskService).markReady(task, response);
    InOrder generationOrder = org.mockito.Mockito.inOrder(
        pendingReplyTaskService,
        customerQueryService,
        skillGatewayService);
    generationOrder.verify(pendingReplyTaskService).beginGeneration("task-1");
    generationOrder.verify(customerQueryService).getByPhone("18800001111");
    generationOrder.verify(skillGatewayService).generateReplies(any());
    generationOrder.verify(pendingReplyTaskService).markReady(task, response);
    generationOrder.verify(pendingReplyTaskService).endGeneration("task-1");
  }

  @Test
  void retryPendingReplyTaskRecordsTheNewReplyWithoutRecordingAnotherCustomerSelection() {
    PendingReplyTask task = claimedTask();
    when(pendingReplyTaskService.claimRetry("task-1", "keeper-1")).thenReturn(task);

    service.retryPendingReplyTask("task-1");

    verify(supervisionEventService, never()).recordPendingEntered(any(), any());
    verify(supervisionEventService, never()).recordCustomerSelected(any(), any(), any());
    verify(supervisionEventService).recordGeneratedReply(
        accessibleCustomer,
        "CHAT_RECOGNIZE",
        "task-1",
        "reply-100-1",
        ChatReplySource.skill(),
        new SkillResponse(List.of(new Suggestion("skill guidance", "NEXT_STEP", "fixed workflow")), null, null, null));
  }

  @Test
  void retryPendingReplyTaskReleasesSelectionWhenTheSelectedCustomerWasDeleted() {
    PendingReplyTask task = claimedTask();
    when(pendingReplyTaskService.claimRetry("task-1", "keeper-1")).thenReturn(task);
    when(customerQueryService.getByPhone("18800001111")).thenReturn(null);

    ApiException exception = assertThrows(
        ApiException.class,
        () -> service.retryPendingReplyTask("task-1"));

    assertEquals(ApiErrorCodes.BAD_REQUEST, exception.getErrorCode());
    InOrder generationOrder = org.mockito.Mockito.inOrder(pendingReplyTaskService, customerQueryService);
    generationOrder.verify(pendingReplyTaskService).beginGeneration("task-1");
    generationOrder.verify(customerQueryService).getByPhone("18800001111");
    generationOrder.verify(pendingReplyTaskService).releaseSelection(task);
    generationOrder.verify(pendingReplyTaskService).endGeneration("task-1");
    verify(skillGatewayService, never()).generateReplies(any());
  }

  @Test
  void retryPendingReplyTaskMarksFailedWhenGenerationThrows() {
    PendingReplyTask task = claimedTask();
    RuntimeException generationFailure = new RuntimeException("skill unavailable");
    when(pendingReplyTaskService.claimRetry("task-1", "keeper-1")).thenReturn(task);
    when(skillGatewayService.generateReplies(any())).thenThrow(generationFailure);

    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> service.retryPendingReplyTask("task-1"));

    org.junit.jupiter.api.Assertions.assertSame(generationFailure, exception);
    InOrder generationOrder = org.mockito.Mockito.inOrder(pendingReplyTaskService, skillGatewayService);
    generationOrder.verify(pendingReplyTaskService).beginGeneration("task-1");
    generationOrder.verify(skillGatewayService).generateReplies(any());
    generationOrder.verify(pendingReplyTaskService).markFailed(task, ApiErrorCodes.INTERNAL_ERROR);
    generationOrder.verify(pendingReplyTaskService).endGeneration("task-1");
    verify(pendingReplyTaskService, never()).markReady(any(), any());
  }

  @Test
  void confirmPendingReplyTaskGeneratesFromTheSelectedCustomerAndOriginalChat() {
    Customer secondCandidate = customer("18800002222");
    PendingReplyTask task = new PendingReplyTask(
        1L,
        "task-1",
        "reply-100-1",
        "keeper-1",
        PendingReplyTaskStatus.GENERATING,
        "同名客户",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "我想了解项目",
        List.of(Map.of("role", "client", "text", "我想了解项目", "timestamp", "12:00")),
        List.of("18800001111", "18800002222"),
        "18800002222",
        null,
        null,
        null,
        java.time.LocalDateTime.of(2026, 7, 23, 10, 0),
        java.time.LocalDateTime.of(2026, 7, 22, 10, 0),
        java.time.LocalDateTime.of(2026, 7, 22, 10, 0));
    when(pendingReplyTaskService.claimForGeneration("task-1", "keeper-1", "18800002222"))
        .thenReturn(task);
    when(customerQueryService.getByPhone("18800002222")).thenReturn(secondCandidate);
    when(customerAccessService.canAccess(secondCandidate)).thenReturn(true);

    ChatResponse response = service.confirmPendingReplyTask(
        "task-1",
        new PendingReplyTaskSelectRequest("18800002222"));

    assertEquals("18800002222", response.phone());
    assertEquals("skill guidance", response.skill().suggestions().get(0).text());
    org.mockito.ArgumentCaptor<SkillRequest> requestCaptor = org.mockito.ArgumentCaptor.forClass(SkillRequest.class);
    verify(skillGatewayService).generateReplies(requestCaptor.capture());
    assertEquals(Scene.CHAT_RECOGNIZE, requestCaptor.getValue().scene());
    assertEquals("18800002222", requestCaptor.getValue().phone());
    assertEquals("18800002222", requestCaptor.getValue().customer().get("phone"));
    assertEquals("我想了解项目", requestCaptor.getValue().clientMessage());
    assertEquals("我想了解项目", requestCaptor.getValue().chatContext().get(0).get("text"));
    verify(pendingReplyTaskService).markReady(eq(task), eq(response));
    verify(supervisionEventService).recordPendingEntered(secondCandidate, "task-1");
    verify(supervisionEventService).recordCustomerSelected(secondCandidate, "task-1", "reply-100-1");
    verify(supervisionEventService).recordGeneratedReply(
        secondCandidate,
        "CHAT_RECOGNIZE",
        "task-1",
        "reply-100-1",
        ChatReplySource.skill(),
        response.skill());
  }

  @Test
  void confirmPendingReplyTaskReleasesSelectionWhenTheCustomerNoLongerExists() {
    PendingReplyTask task = claimedTask();
    when(pendingReplyTaskService.claimForGeneration("task-1", "keeper-1", "18800001111"))
        .thenReturn(task);
    when(customerQueryService.getByPhone("18800001111")).thenReturn(null);

    ApiException exception = assertThrows(ApiException.class, () -> service.confirmPendingReplyTask(
        "task-1",
        new PendingReplyTaskSelectRequest("18800001111")));

    assertEquals(ApiErrorCodes.BAD_REQUEST, exception.getErrorCode());
    InOrder generationOrder = org.mockito.Mockito.inOrder(pendingReplyTaskService, customerQueryService);
    generationOrder.verify(pendingReplyTaskService).beginGeneration("task-1");
    generationOrder.verify(customerQueryService).getByPhone("18800001111");
    generationOrder.verify(pendingReplyTaskService).releaseSelection(task);
    generationOrder.verify(pendingReplyTaskService).endGeneration("task-1");
    verify(skillGatewayService, never()).generateReplies(any());
    verify(pendingReplyTaskService, never()).markReady(any(), any());
  }

  @Test
  void confirmPendingReplyTaskReleasesSelectionWhenTheCustomerIsNoLongerAccessible() {
    PendingReplyTask task = claimedTask();
    Customer inaccessibleCustomer = customer("18800001111");
    when(pendingReplyTaskService.claimForGeneration("task-1", "keeper-1", "18800001111"))
        .thenReturn(task);
    when(customerQueryService.getByPhone("18800001111")).thenReturn(inaccessibleCustomer);
    when(customerAccessService.canAccess(inaccessibleCustomer)).thenReturn(false);

    ApiException exception = assertThrows(ApiException.class, () -> service.confirmPendingReplyTask(
        "task-1",
        new PendingReplyTaskSelectRequest("18800001111")));

    assertEquals(ApiErrorCodes.FORBIDDEN, exception.getErrorCode());
    InOrder generationOrder = org.mockito.Mockito.inOrder(
        pendingReplyTaskService,
        customerQueryService,
        customerAccessService);
    generationOrder.verify(pendingReplyTaskService).beginGeneration("task-1");
    generationOrder.verify(customerQueryService).getByPhone("18800001111");
    generationOrder.verify(customerAccessService).canAccess(inaccessibleCustomer);
    generationOrder.verify(pendingReplyTaskService).releaseSelection(task);
    generationOrder.verify(pendingReplyTaskService).endGeneration("task-1");
    verify(skillGatewayService, never()).generateReplies(any());
    verify(pendingReplyTaskService, never()).markReady(any(), any());
  }

  @Test
  void confirmPendingReplyTaskMarksFailedWhenSkillGenerationThrows() {
    PendingReplyTask task = claimedTask();
    when(pendingReplyTaskService.claimForGeneration("task-1", "keeper-1", "18800001111"))
        .thenReturn(task);
    when(skillGatewayService.generateReplies(any())).thenThrow(new RuntimeException("skill unavailable"));

    RuntimeException exception = assertThrows(RuntimeException.class, () -> service.confirmPendingReplyTask(
        "task-1",
        new PendingReplyTaskSelectRequest("18800001111")));

    assertEquals("skill unavailable", exception.getMessage());
    verify(pendingReplyTaskService).markFailed(task, ApiErrorCodes.INTERNAL_ERROR);
    verify(pendingReplyTaskService, never()).markReady(any(), any());
  }

  @Test
  void confirmPendingReplyTaskPreservesApiExceptionCodeWhenSkillGenerationThrows() {
    PendingReplyTask task = claimedTask();
    ApiException upstreamFailure = new ApiException("30-12345", "upstream failed");
    when(pendingReplyTaskService.claimForGeneration("task-1", "keeper-1", "18800001111"))
        .thenReturn(task);
    when(skillGatewayService.generateReplies(any())).thenThrow(upstreamFailure);

    ApiException exception = assertThrows(ApiException.class, () -> service.confirmPendingReplyTask(
        "task-1",
        new PendingReplyTaskSelectRequest("18800001111")));

    org.junit.jupiter.api.Assertions.assertSame(upstreamFailure, exception);
    verify(pendingReplyTaskService).markFailed(task, "30-12345");
    verify(pendingReplyTaskService, never()).markReady(any(), any());
  }

  @Test
  void confirmPendingReplyTaskMarksFailedWhenSavingReadyResultThrows() {
    PendingReplyTask task = claimedTask();
    RuntimeException readyFailure = new RuntimeException("ready write failed");
    when(pendingReplyTaskService.claimForGeneration("task-1", "keeper-1", "18800001111"))
        .thenReturn(task);
    org.mockito.Mockito.doThrow(readyFailure).when(pendingReplyTaskService).markReady(eq(task), any());

    RuntimeException exception = assertThrows(RuntimeException.class, () -> service.confirmPendingReplyTask(
        "task-1",
        new PendingReplyTaskSelectRequest("18800001111")));

    org.junit.jupiter.api.Assertions.assertSame(readyFailure, exception);
    InOrder generationOrder = org.mockito.Mockito.inOrder(pendingReplyTaskService);
    generationOrder.verify(pendingReplyTaskService).beginGeneration("task-1");
    generationOrder.verify(pendingReplyTaskService).markReady(eq(task), any());
    generationOrder.verify(pendingReplyTaskService).markFailed(task, ApiErrorCodes.INTERNAL_ERROR);
    generationOrder.verify(pendingReplyTaskService).endGeneration("task-1");
  }

  @Test
  void confirmPendingReplyTaskMarksFailedWhenCustomerLookupThrows() {
    PendingReplyTask task = claimedTask();
    RuntimeException lookupFailure = new RuntimeException("customer lookup failed");
    when(pendingReplyTaskService.claimForGeneration("task-1", "keeper-1", "18800001111"))
        .thenReturn(task);
    when(customerQueryService.getByPhone("18800001111")).thenThrow(lookupFailure);

    RuntimeException exception = assertThrows(RuntimeException.class, () -> service.confirmPendingReplyTask(
        "task-1",
        new PendingReplyTaskSelectRequest("18800001111")));

    org.junit.jupiter.api.Assertions.assertSame(lookupFailure, exception);
    InOrder generationOrder = org.mockito.Mockito.inOrder(pendingReplyTaskService, customerQueryService);
    generationOrder.verify(pendingReplyTaskService).beginGeneration("task-1");
    generationOrder.verify(customerQueryService).getByPhone("18800001111");
    generationOrder.verify(pendingReplyTaskService).markFailed(task, ApiErrorCodes.INTERNAL_ERROR);
    generationOrder.verify(pendingReplyTaskService).endGeneration("task-1");
    verify(pendingReplyTaskService, never()).releaseSelection(task);
  }

  @Test
  void confirmPendingReplyTaskMarksFailedWhenCustomerAccessCheckThrows() {
    PendingReplyTask task = claimedTask();
    RuntimeException accessFailure = new RuntimeException("customer access failed");
    when(pendingReplyTaskService.claimForGeneration("task-1", "keeper-1", "18800001111"))
        .thenReturn(task);
    when(customerAccessService.canAccess(accessibleCustomer)).thenThrow(accessFailure);

    RuntimeException exception = assertThrows(RuntimeException.class, () -> service.confirmPendingReplyTask(
        "task-1",
        new PendingReplyTaskSelectRequest("18800001111")));

    org.junit.jupiter.api.Assertions.assertSame(accessFailure, exception);
    InOrder generationOrder = org.mockito.Mockito.inOrder(
        pendingReplyTaskService,
        customerQueryService,
        customerAccessService);
    generationOrder.verify(pendingReplyTaskService).beginGeneration("task-1");
    generationOrder.verify(customerQueryService).getByPhone("18800001111");
    generationOrder.verify(customerAccessService).canAccess(accessibleCustomer);
    generationOrder.verify(pendingReplyTaskService).markFailed(task, ApiErrorCodes.INTERNAL_ERROR);
    generationOrder.verify(pendingReplyTaskService).endGeneration("task-1");
    verify(pendingReplyTaskService, never()).releaseSelection(task);
  }

  @Test
  void confirmPendingReplyTaskPreservesOriginalFailureWhenMarkFailedAlsoThrows() {
    PendingReplyTask task = claimedTask();
    RuntimeException skillFailure = new RuntimeException("skill unavailable");
    when(pendingReplyTaskService.claimForGeneration("task-1", "keeper-1", "18800001111"))
        .thenReturn(task);
    when(skillGatewayService.generateReplies(any())).thenThrow(skillFailure);
    org.mockito.Mockito.doThrow(new RuntimeException("failed state write failed"))
        .when(pendingReplyTaskService)
        .markFailed(task, ApiErrorCodes.INTERNAL_ERROR);

    RuntimeException exception = assertThrows(RuntimeException.class, () -> service.confirmPendingReplyTask(
        "task-1",
        new PendingReplyTaskSelectRequest("18800001111")));

    org.junit.jupiter.api.Assertions.assertSame(skillFailure, exception);
    verify(pendingReplyTaskService).markFailed(task, ApiErrorCodes.INTERNAL_ERROR);
  }

  @Test
  void recognizePersistsRawMessagesForLaterSendConfirmation() {
    when(imageRecognitionService.recognize(any(), any())).thenReturn(new RecognitionResult(
        "Alice",
        "18800004444",
        List.of(),
        "12:00"));
    when(customerMatchService.match(any())).thenReturn(com.privateflow.modules.match.MatchResult.none());
    when(skillGatewayService.generateReplies(any())).thenReturn(new SkillResponse(
        List.of(new Suggestion("reply", "NEXT_STEP", "reason")),
        null,
        null,
        null));

    service.recognize(new ChatRecognizeRequest(
        Base64.getEncoder().encodeToString("image".getBytes()),
        "客户想了解项目",
        "Alice",
        "TUAN_GOU",
        "私域客资管理表",
        List.of(
            new ChatMessageDto("client", "客户真实原话", "12:00"),
            new ChatMessageDto("keeper", "员工上一轮回复", "12:01"))));

    org.mockito.ArgumentCaptor<RequestContext> captor = org.mockito.ArgumentCaptor.forClass(RequestContext.class);
    verify(contextStore).save(eq("keeper-1"), eq("18800004444"), captor.capture());
    assertEquals(2, captor.getValue().request().chatContext().size());
    assertEquals("客户真实原话", captor.getValue().request().chatContext().get(0).get("text"));
  }

  @Test
  void generatePassesCurrentReplyTagsToTheReplyRequest() {
    Customer customer = customer("18800001111");
    customer.setId(5L);
    ReplyTagSnapshot tag = replyTag("LOYALIST", "\\u5fe0\\u8bda\\u578b");
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    when(replyTagSnapshotBuilder.build(5L)).thenReturn(List.of(tag));
    when(skillGatewayService.generateReplies(any())).thenReturn(new SkillResponse(
        List.of(new Suggestion("skill reply", "NEXT_STEP", "reason")),
        null,
        null,
        null));

    service.generate(new GenerateRequest("18800001111", "ACTIVE_REPLY", "hello"));

    org.mockito.ArgumentCaptor<SkillRequest> captor =
        org.mockito.ArgumentCaptor.forClass(SkillRequest.class);
    verify(skillGatewayService).generateReplies(captor.capture());
    assertEquals(List.of(tag), captor.getValue().currentTags());
  }

  @Test
  void tagReadFailureKeepsReplyFlowAndRecordsDegradation() {
    Customer customer = customer("18800001111");
    customer.setId(5L);
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    when(replyTagSnapshotBuilder.build(5L))
        .thenThrow(new IllegalStateException("directory unavailable"));
    when(skillGatewayService.generateReplies(any())).thenReturn(new SkillResponse(
        List.of(new Suggestion("ordinary reply", "NEXT_STEP", "reason")),
        null,
        null,
        null));

    ChatResponse response = service.generate(
        new GenerateRequest("18800001111", "ACTIVE_REPLY", "hello"));

    assertEquals("ordinary reply", response.skill().suggestions().get(0).text());
    verify(auditLogger).log(
        "CUSTOMER_TAGS_READ_DEGRADED",
        "keeper-1",
        "CUSTOMER",
        "18800001111",
        "directory unavailable");
  }

  @Test
  void tagAccessApiExceptionIsNotDegraded() {
    Customer customer = customer("18800001111");
    customer.setId(5L);
    ApiException forbidden = new ApiException(ApiErrorCodes.FORBIDDEN, "forbidden");
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    when(replyTagSnapshotBuilder.build(5L)).thenThrow(forbidden);

    ApiException actual = assertThrows(
        ApiException.class,
        () -> service.generate(new GenerateRequest("18800001111", "ACTIVE_REPLY", "hello")));

    assertEquals(ApiErrorCodes.FORBIDDEN, actual.getErrorCode());
    verify(skillGatewayService, never()).generateReplies(any());
    verify(auditLogger, never()).log(eq("CUSTOMER_TAGS_READ_DEGRADED"), any(), any(), any(), any());
  }

  @Test
  void regenerateReloadsLatestCustomerTagsInsteadOfReusingStoredSnapshot() {
    Customer latest = customer("18800001111");
    latest.setId(5L);
    latest.setNickname("Latest");
    ReplyTagSnapshot oldTag = replyTag("LOYALIST", "\\u5fe0\\u8bda\\u578b");
    ReplyTagSnapshot latestTag = replyTag("DECISIVE", "\\u679c\\u65ad\\u578b");
    SkillRequest previous = new SkillRequest(
        Scene.ACTIVE_REPLY,
        "TUAN_GOU",
        "18800001111",
        "hello",
        Map.of("nickname", "Alice"),
        Map.of(),
        List.of(),
        List.of(),
        "keeper",
        List.of(oldTag));
    SkillResponse previousResponse = new SkillResponse(
        List.of(new Suggestion("old", "NEXT_STEP", "reason")),
        null,
        null,
        null);
    when(contextStore.read("keeper-1", "18800001111"))
        .thenReturn(Optional.of(new RequestContext(previous, previousResponse, 0)));
    when(customerQueryService.getByPhone("18800001111")).thenReturn(latest);
    when(replyTagSnapshotBuilder.build(5L)).thenReturn(List.of(latestTag));
    when(skillGatewayService.generateReplies(any())).thenReturn(new SkillResponse(
        List.of(new Suggestion("new", "NEXT_STEP", "reason")),
        null,
        null,
        null));

    service.regenerate(new RegenerateRequest("18800001111"));

    org.mockito.ArgumentCaptor<SkillRequest> captor =
        org.mockito.ArgumentCaptor.forClass(SkillRequest.class);
    verify(skillGatewayService).generateReplies(captor.capture());
    assertEquals(List.of(latestTag), captor.getValue().currentTags());
    assertEquals("Latest", captor.getValue().customer().get("nickname"));
  }

  @Test
  void regenerateWarningUsesConfigCenterMaxCount() {
    when(skillConfigProvider.get()).thenReturn(skillConfig(5));
    SkillRequest previousRequest = new SkillRequest(
        Scene.ACTIVE_REPLY,
        "TUAN_GOU",
        "18800001111",
        "hello",
        Map.of("nickname", "Alice"),
        Map.of(),
        List.of(),
        List.of(),
        "keeper");
    SkillResponse previousResponse = new SkillResponse(List.of(new Suggestion("old", "NEXT_STEP", "reason")), null, null, null);
    when(contextStore.read(eq("keeper-1"), eq("18800001111")))
        .thenReturn(Optional.of(new RequestContext(previousRequest, previousResponse, 3)))
        .thenReturn(Optional.of(new RequestContext(previousRequest, previousResponse, 4)));
    when(skillGatewayService.generateReplies(any())).thenReturn(new SkillResponse(
        List.of(new Suggestion("new", "NEXT_STEP", "reason")),
        null,
        null,
        null));

    ChatResponse fourth = service.regenerate(new RegenerateRequest("18800001111"));
    ChatResponse fifth = service.regenerate(new RegenerateRequest("18800001111"));

    assertNull(fourth.warning());
    assertEquals("已连续换 5 次，可以尝试求助组长", fifth.warning());
  }

  @Test
  void regenerateFallsBackToSkillWhenLlmIsDisabledOrUnavailable() {
    SkillRequest previousRequest = new SkillRequest(
        Scene.ACTIVE_REPLY,
        "TUAN_GOU",
        "18800001111",
        "hello",
        Map.of("nickname", "Alice"),
        Map.of(),
        List.of(),
        List.of(),
        "keeper");
    SkillResponse previousResponse = new SkillResponse(List.of(new Suggestion("old", "NEXT_STEP", "reason")), null, null, null);
    when(contextStore.read(eq("keeper-1"), eq("18800001111"))).thenReturn(Optional.of(new RequestContext(previousRequest, previousResponse, 0)));
    when(skillGatewayService.generateReplies(any())).thenReturn(new SkillResponse(
        List.of(new Suggestion("skill reply", "NEXT_STEP", "reason")),
        null,
        null,
        null));

    ChatResponse response = service.regenerate(new RegenerateRequest("18800001111"));

    assertEquals("skill reply", response.skill().suggestions().get(0).text());
    assertEquals("SKILL", response.replySource().source());
    verify(llmReplyGenerationService).tryGenerate(any(), any());
    verify(skillGatewayService).generateReplies(any());
  }

  @Test
  void regenerateUsesSkillGuidanceBeforeLlmReplyGeneration() {
    SkillRequest previousRequest = new SkillRequest(
        Scene.ACTIVE_REPLY,
        "TUAN_GOU",
        "18800001111",
        "hello",
        Map.of("nickname", "Alice"),
        Map.of(),
        List.of(),
        List.of(),
        "keeper");
    SkillResponse previousResponse = new SkillResponse(List.of(new Suggestion("old", "NEXT_STEP", "reason")), null, null, null);
    SkillResponse skillGuidance = new SkillResponse(
        List.of(new Suggestion("先确认恢复阶段", "NEXT_STEP", "固定流程")),
        null,
        null,
        null);
    SkillResponse llmResponse = new SkillResponse(List.of(new Suggestion("llm reply", "NEXT_STEP", "reason")), null, null, null);
    when(contextStore.read(eq("keeper-1"), eq("18800001111"))).thenReturn(Optional.of(new RequestContext(previousRequest, previousResponse, 0)));
    when(skillGatewayService.generateReplies(any())).thenReturn(skillGuidance);
    when(llmReplyGenerationService.tryGenerate(any(), eq(skillGuidance))).thenReturn(Optional.of(llmResponse));

    ChatResponse response = service.regenerate(new RegenerateRequest("18800001111"));

    assertEquals("llm reply", response.skill().suggestions().get(0).text());
    assertEquals("LLM", response.replySource().source());
    org.mockito.InOrder order = org.mockito.Mockito.inOrder(skillGatewayService, llmReplyGenerationService);
    order.verify(skillGatewayService).generateReplies(any());
    order.verify(llmReplyGenerationService).tryGenerate(any(), eq(skillGuidance));
  }

  @Test
  void regenerateUsesMcpExecutionGuidanceBeforeLlmReplyGeneration() {
    SkillRequest previousRequest = new SkillRequest(
        Scene.ACTIVE_REPLY,
        "TUAN_GOU",
        "18800001111",
        "hello",
        Map.of("nickname", "Alice"),
        Map.of(),
        List.of(),
        List.of(),
        "keeper");
    SkillResponse previousResponse = new SkillResponse(List.of(new Suggestion("old", "NEXT_STEP", "reason")), null, null, null);
    SkillResponse skillGuidance = SkillResponse.guidanceOnly("先确认客户预算顾虑，再追问真实预算和优先目标。");
    SkillResponse llmResponse = new SkillResponse(List.of(new Suggestion("llm reply", "NEXT_STEP", "reason")), null, null, null);
    when(contextStore.read(eq("keeper-1"), eq("18800001111"))).thenReturn(Optional.of(new RequestContext(previousRequest, previousResponse, 0)));
    when(skillGatewayService.generateReplies(any())).thenReturn(skillGuidance);
    when(llmReplyGenerationService.tryGenerate(any(), eq(skillGuidance))).thenReturn(Optional.of(llmResponse));

    ChatResponse response = service.regenerate(new RegenerateRequest("18800001111"));

    assertEquals("llm reply", response.skill().suggestions().get(0).text());
    assertEquals("LLM", response.replySource().source());
    verify(llmReplyGenerationService).tryGenerate(any(), eq(skillGuidance));
  }

  @Test
  void usesConfiguredFallbackWhenMcpGuidanceHasNoLlmReply() {
    SkillRequest previousRequest = new SkillRequest(
        Scene.ACTIVE_REPLY,
        "TUAN_GOU",
        "18800001111",
        "hello",
        Map.of("nickname", "Alice"),
        Map.of(),
        List.of(),
        List.of(),
        "keeper");
    SkillResponse previousResponse = new SkillResponse(List.of(new Suggestion("old", "NEXT_STEP", "reason")), null, null, null);
    SkillResponse skillGuidance = SkillResponse.guidanceOnly("内部固定流程内容");
    when(contextStore.read(eq("keeper-1"), eq("18800001111"))).thenReturn(Optional.of(new RequestContext(previousRequest, previousResponse, 0)));
    when(skillGatewayService.generateReplies(any())).thenReturn(skillGuidance);
    when(llmReplyGenerationService.tryGenerate(any(), eq(skillGuidance))).thenReturn(Optional.empty());

    ChatResponse response = service.regenerate(new RegenerateRequest("18800001111"));

    assertEquals("fallback", response.skill().suggestions().get(0).text());
    assertEquals("FALLBACK", response.replySource().source());
  }

  @Test
  void regenerateMarksFallbackSourceWhenSkillReturnsSystemFallback() {
    SkillRequest previousRequest = new SkillRequest(
        Scene.ACTIVE_REPLY,
        "TUAN_GOU",
        "18800001111",
        "hello",
        Map.of("nickname", "Alice"),
        Map.of(),
        List.of(),
        List.of(),
        "keeper");
    SkillResponse previousResponse = new SkillResponse(List.of(new Suggestion("old", "NEXT_STEP", "reason")), null, null, null);
    when(contextStore.read(eq("keeper-1"), eq("18800001111"))).thenReturn(Optional.of(new RequestContext(previousRequest, previousResponse, 0)));
    when(skillGatewayService.generateReplies(any())).thenReturn(new SkillResponse(
        List.of(new Suggestion("fallback", "SYSTEM_FALLBACK", "")),
        null,
        null,
        null));

    ChatResponse response = service.regenerate(new RegenerateRequest("18800001111"));

    assertEquals("FALLBACK", response.replySource().source());
    verify(llmReplyGenerationService, never()).tryGenerate(any(), any());
  }

  @Test
  void sendConfirmPublishesFullPhoneEventForProfileAndTableWriteConsumers() {
    SendConfirmRequest request = new SendConfirmRequest(
        "18800001111",
        "Alice",
        false,
        "私域客资管理表",
        "TUAN_GOU",
        "前端已生成摘要",
        List.of(new ChatMessageDto("client", "客户问到店评估", "12:00")),
        "建议今天预约到店评估",
        "NEXT_STEP",
        new CustomerMessageSentEvent.FollowupSuggestPayload("2026-07-10T10:00:00", "预约到店"),
        true);

    Map<String, Object> result = service.sendConfirm(request);

    assertEquals(true, result.get("accepted"));
    org.mockito.ArgumentCaptor<CustomerMessageSentEvent> captor = org.mockito.ArgumentCaptor.forClass(CustomerMessageSentEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    CustomerMessageSentEvent event = captor.getValue();
    assertEquals("18800001111", event.phone());
    assertEquals("前端已生成摘要", event.conversationSummary());
    assertEquals("NEXT_STEP", event.selectedDirection());
    assertEquals("预约到店", event.followupSuggest().nextFollowupDir());
    assertEquals(true, event.completeCurrentFollowup());
    assertEquals("keeper-1", event.operator());
    org.mockito.InOrder persistenceOrder = org.mockito.Mockito.inOrder(followupConfirmationService, eventPublisher);
    persistenceOrder.verify(followupConfirmationService).record(
        accessibleCustomer,
        "前端已生成摘要",
        "建议今天预约到店评估",
        new CustomerMessageSentEvent.FollowupSuggestPayload("2026-07-10T10:00:00", "预约到店"),
        true);
    persistenceOrder.verify(eventPublisher).publishEvent(event);
    verify(llmFollowupSuggestionService, never()).trySuggest(any());
    verify(llmSummaryService, never()).trySummarize(any());
    verify(auditLogger).log("SEND_CONFIRM", "keeper-1", "CUSTOMER", "18800001111", "message sent");
    verify(supervisionEventService, never()).recordAiUsage(any());
  }

  @Test
  void sendConfirmUsesLlmFollowupSuggestionWhenRequestHasNoSuggestion() {
    when(llmFollowupSuggestionService.trySuggest(any(LlmFollowupSuggestionInput.class))).thenReturn(Optional.of(
        new CustomerMessageSentEvent.FollowupSuggestPayload("2026-07-11T09:00:00", "二次确认到店时间")));
    SendConfirmRequest request = new SendConfirmRequest(
        "18800001111",
        "Alice",
        false,
        "私域客资管理表",
        "TUAN_GOU",
        "客户说需要考虑一下",
        List.of(new ChatMessageDto("client", "我再考虑一下", "12:00")),
        "好的，我明天再和你确认时间",
        "NEXT_STEP",
        null);

    service.sendConfirm(request);

    org.mockito.ArgumentCaptor<CustomerMessageSentEvent> captor = org.mockito.ArgumentCaptor.forClass(CustomerMessageSentEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    CustomerMessageSentEvent event = captor.getValue();
    assertEquals("2026-07-11T09:00:00", event.followupSuggest().nextFollowupAt());
    assertEquals("二次确认到店时间", event.followupSuggest().nextFollowupDir());
  }

  @Test
  void sendConfirmUsesLlmSummaryWhenRequestHasNoConversationSummary() {
    when(llmSummaryService.trySummarize(any(LlmSummaryInput.class))).thenReturn(Optional.of("客户仍在考虑，明天继续确认到店时间"));
    SendConfirmRequest request = new SendConfirmRequest(
        "18800001111",
        "Alice",
        false,
        "私域客资管理表",
        "TUAN_GOU",
        "",
        List.of(new ChatMessageDto("client", "我再考虑一下", "12:00")),
        "好的，我明天再和你确认时间",
        "NEXT_STEP",
        null);

    service.sendConfirm(request);

    org.mockito.ArgumentCaptor<CustomerMessageSentEvent> captor = org.mockito.ArgumentCaptor.forClass(CustomerMessageSentEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertEquals("客户仍在考虑，明天继续确认到店时间", captor.getValue().conversationSummary());
  }

  @Test
  void sendConfirmRejectsInaccessibleCustomerBeforeAnyExternalOrEventSideEffect() {
    Customer inaccessible = customer("18800002222");
    when(customerQueryService.getByPhone("18800002222")).thenReturn(inaccessible);
    when(customerAccessService.canAccess(inaccessible)).thenReturn(false);

    ApiException exception = assertThrows(ApiException.class, () -> service.sendConfirm(new SendConfirmRequest(
        "18800002222",
        "无权客户",
        false,
        "私域客资管理表",
        "TUAN_GOU",
        "",
        List.of(new ChatMessageDto("client", "客户原话", "12:00")),
        "员工发送内容",
        "NEXT_STEP",
        null)));

    assertEquals("80-10003", exception.getErrorCode());
    verify(llmSummaryService, never()).trySummarize(any());
    verify(llmFollowupSuggestionService, never()).trySuggest(any());
    verify(eventPublisher, never()).publishEvent(any());
    verify(auditLogger, never()).log(eq("SEND_CONFIRM"), any(), any(), any(), any());
  }

  @Test
  void sendConfirmRejectsUnrecognizedNewCustomerBeforeAnySideEffect() {
    when(customerQueryService.getByPhone("18800003333")).thenReturn(null);
    when(contextStore.read("keeper-1", "18800003333")).thenReturn(Optional.empty());

    ApiException exception = assertThrows(ApiException.class, () -> service.sendConfirm(new SendConfirmRequest(
        "18800003333",
        "新客户",
        true,
        "私域客资管理表",
        "TUAN_GOU",
        "",
        List.of(new ChatMessageDto("client", "客户想了解项目", "12:00")),
        "员工发送内容",
        "NEXT_STEP",
        null)));

    assertEquals("80-10003", exception.getErrorCode());
    verify(eventPublisher, never()).publishEvent(any());
    verify(auditLogger, never()).log(eq("SEND_CONFIRM"), any(), any(), any(), any());
  }

  @Test
  void sendConfirmAllowsNewCustomerFromCurrentUsersRecognizeContext() {
    when(customerQueryService.getByPhone("18800003333")).thenReturn(null);
    when(contextStore.read("keeper-1", "18800003333"))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(RequestContext.class)));

    Map<String, Object> result = service.sendConfirm(new SendConfirmRequest(
        "18800003333",
        "新客户",
        true,
        "私域客资管理表",
        "TUAN_GOU",
        "",
        List.of(new ChatMessageDto("client", "客户想了解项目", "12:00")),
        "员工发送内容",
        "NEXT_STEP",
        null));

    assertEquals(true, result.get("accepted"));
    verify(eventPublisher).publishEvent(any(CustomerMessageSentEvent.class));
    verify(followupConfirmationService, never()).record(any(), any(), any(), any(), anyBoolean());
  }

  @Test
  void sendConfirmFallsBackToOnlyRealCustomerMessagesWhenSummaryLlmIsUnavailable() {
    SendConfirmRequest request = new SendConfirmRequest(
        "18800001111",
        "Alice",
        false,
        "私域客资管理表",
        "TUAN_GOU",
        "",
        List.of(
            new ChatMessageDto("client", "客户说我再考虑一下", "12:00"),
            new ChatMessageDto("keeper", "员工说可以慢慢考虑", "12:01"),
            new ChatMessageDto("customer", "客户说下周再联系", "12:02"),
            new ChatMessageDto("staff", "员工说好的", "12:03")),
        "员工最终发送的回复",
        "NEXT_STEP",
        null);

    service.sendConfirm(request);

    org.mockito.ArgumentCaptor<CustomerMessageSentEvent> captor = org.mockito.ArgumentCaptor.forClass(CustomerMessageSentEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    String summary = captor.getValue().conversationSummary();
    org.junit.jupiter.api.Assertions.assertTrue(summary.contains("客户说我再考虑一下"));
    org.junit.jupiter.api.Assertions.assertTrue(summary.contains("客户说下周再联系"));
    org.junit.jupiter.api.Assertions.assertFalse(summary.contains("员工说可以慢慢考虑"));
    org.junit.jupiter.api.Assertions.assertFalse(summary.contains("员工说好的"));
    org.junit.jupiter.api.Assertions.assertFalse(summary.contains("员工最终发送的回复"));
    assertEquals(4, captor.getValue().rawMessages().size());
  }

  @Test
  void sendConfirmKeepsSummaryEmptyWhenThereAreNoCustomerMessages() {
    service.sendConfirm(new SendConfirmRequest(
        "18800001111",
        "Alice",
        false,
        "私域客资管理表",
        "TUAN_GOU",
        "",
        List.of(new ChatMessageDto("keeper", "员工跟进内容", "12:01")),
        "员工最终发送的回复",
        "NEXT_STEP",
        null));

    org.mockito.ArgumentCaptor<CustomerMessageSentEvent> captor = org.mockito.ArgumentCaptor.forClass(CustomerMessageSentEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertEquals("", captor.getValue().conversationSummary());
  }

  @Test
  void sendConfirmPrefersCurrentUsersRequestContextOverRequestBodyMessages() {
    SkillRequest previousRequest = new SkillRequest(
        Scene.CHAT_RECOGNIZE,
        "TUAN_GOU",
        "18800001111",
        "客户想了解项目",
        Map.of(),
        Map.of(),
        List.of(),
        List.of(
            Map.of("role", "client", "text", "客户真实原话", "timestamp", "12:00"),
            Map.of("role", "keeper", "text", "员工上一轮回复", "timestamp", "12:01")),
        "keeper-1");
    when(contextStore.read("keeper-1", "18800001111")).thenReturn(Optional.of(new RequestContext(
        previousRequest,
        new SkillResponse(List.of(), null, null, null),
        0)));

    service.sendConfirm(new SendConfirmRequest(
        "18800001111",
        "Alice",
        false,
        "私域客资管理表",
        "TUAN_GOU",
        "",
        List.of(new ChatMessageDto("client", "请求体伪造内容", "13:00")),
        "员工最终发送的回复",
        "NEXT_STEP",
        null));

    org.mockito.ArgumentCaptor<CustomerMessageSentEvent> captor = org.mockito.ArgumentCaptor.forClass(CustomerMessageSentEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertEquals(2, captor.getValue().rawMessages().size());
    assertEquals("客户真实原话", captor.getValue().rawMessages().get(0).text());
    assertEquals("客户真实原话", captor.getValue().conversationSummary());
  }

  private SkillConfig skillConfig(int regenerateMaxCount) {
    return new SkillConfig(
        "",
        "",
        "LAST_FOUR",
        "",
        10000,
        30,
        0.5,
        5,
        30,
        "fallback",
        "",
        "",
        "",
        "prompt",
        "",
        0.3,
        15,
        8000,
        regenerateMaxCount);
  }

  private Customer customer(String phone) {
    Customer customer = new Customer();
    customer.setPhone(phone);
    customer.setNickname("测试客户");
    customer.setAssignedKeeper("keeper-1");
    return customer;
  }

  private PendingReplyTask claimedTask() {
    return new PendingReplyTask(
        1L,
        "task-1",
        "reply-100-1",
        "keeper-1",
        PendingReplyTaskStatus.GENERATING,
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more", "timestamp", "12:00")),
        List.of("18800001111", "18800002222"),
        "18800001111",
        null,
        null,
        null,
        java.time.LocalDateTime.of(2026, 7, 23, 10, 0),
        java.time.LocalDateTime.of(2026, 7, 22, 10, 0),
        java.time.LocalDateTime.of(2026, 7, 22, 10, 0));
  }

  private PendingReplyTaskView pendingTaskView(PendingReplyTaskStatus status, ChatResponse response) {
    return new PendingReplyTaskView(
        "task-1",
        "reply-100-1",
        status,
        List.of(),
        status == PendingReplyTaskStatus.WAITING_CUSTOMER ? null : "18800001111",
        response,
        null,
        java.time.LocalDateTime.of(2026, 7, 23, 10, 0));
  }

  private ReplyTagSnapshot replyTag(String value, String displayName) {
    return new ReplyTagSnapshot(
        "personality_type",
        "\\u6027\\u683c\\u7c7b\\u578b",
        value,
        displayName,
        "\\u91cd\\u89c6\\u5b89\\u5168\\u611f",
        "MANUAL",
        "\\u5ba2\\u6237\\u8bc1\\u636e",
        true);
  }
}
