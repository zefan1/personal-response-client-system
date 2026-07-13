package com.privateflow.modules.api.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.image.ImageErrorCodes;
import com.privateflow.modules.image.ImageRecognitionException;
import com.privateflow.modules.image.ImageRecognitionService;
import com.privateflow.modules.llm.LlmReplyGenerationService;
import com.privateflow.modules.llm.LlmFollowupSuggestionInput;
import com.privateflow.modules.llm.LlmFollowupSuggestionService;
import com.privateflow.modules.llm.LlmSummaryInput;
import com.privateflow.modules.llm.LlmSummaryService;
import com.privateflow.modules.match.CustomerMatchService;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillGatewayService;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.Suggestion;
import com.privateflow.modules.skill.config.SkillConfig;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    when(skillConfigProvider.get()).thenReturn(skillConfig(3));
    when(llmReplyGenerationService.tryGenerate(any())).thenReturn(Optional.empty());
    when(llmReplyGenerationService.fallbackToSkill()).thenReturn(true);
    when(llmFollowupSuggestionService.trySuggest(any())).thenReturn(Optional.empty());
    when(llmSummaryService.trySummarize(any())).thenReturn(Optional.empty());
    service = new ChatOrchestrationService(
        imageRecognitionService,
        customerMatchService,
        skillGatewayService,
        org.mockito.Mockito.mock(CustomerQueryService.class),
        contextStore,
        eventPublisher,
        auditLogger,
        skillConfigProvider,
        llmReplyGenerationService,
        llmFollowupSuggestionService,
        llmSummaryService);
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
    verify(llmReplyGenerationService).tryGenerate(any());
    verify(skillGatewayService).generateReplies(any());
  }

  @Test
  void regenerateUsesLlmReplyWhenAvailableAndSkipsSkill() {
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
    SkillResponse llmResponse = new SkillResponse(List.of(new Suggestion("llm reply", "NEXT_STEP", "reason")), null, null, null);
    when(contextStore.read(eq("keeper-1"), eq("18800001111"))).thenReturn(Optional.of(new RequestContext(previousRequest, previousResponse, 0)));
    when(llmReplyGenerationService.tryGenerate(any())).thenReturn(Optional.of(llmResponse));

    ChatResponse response = service.regenerate(new RegenerateRequest("18800001111"));

    assertEquals("llm reply", response.skill().suggestions().get(0).text());
    assertEquals("LLM", response.replySource().source());
    verify(skillGatewayService, never()).generateReplies(any());
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
        new CustomerMessageSentEvent.FollowupSuggestPayload("2026-07-10T10:00:00", "预约到店"));

    Map<String, Object> result = service.sendConfirm(request);

    assertEquals(true, result.get("accepted"));
    org.mockito.ArgumentCaptor<CustomerMessageSentEvent> captor = org.mockito.ArgumentCaptor.forClass(CustomerMessageSentEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    CustomerMessageSentEvent event = captor.getValue();
    assertEquals("18800001111", event.phone());
    assertEquals("前端已生成摘要", event.conversationSummary());
    assertEquals("NEXT_STEP", event.selectedDirection());
    assertEquals("预约到店", event.followupSuggest().nextFollowupDir());
    assertEquals("keeper-1", event.operator());
    verify(llmFollowupSuggestionService, never()).trySuggest(any());
    verify(llmSummaryService, never()).trySummarize(any());
    verify(auditLogger).log("SEND_CONFIRM", "keeper-1", "CUSTOMER", "18800001111", "message sent");
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
}
