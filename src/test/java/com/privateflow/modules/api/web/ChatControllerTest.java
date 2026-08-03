package com.privateflow.modules.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.chat.AiUsageRequest;
import com.privateflow.modules.api.chat.ChatOrchestrationService;
import com.privateflow.modules.api.chat.ChatReplySource;
import com.privateflow.modules.api.chat.ChatRecognizeRequest;
import com.privateflow.modules.api.chat.ChatResponse;
import com.privateflow.modules.api.chat.RecognitionJobService;
import com.privateflow.modules.api.chat.RecognitionJobStatus;
import com.privateflow.modules.api.chat.RecognitionJobView;
import com.privateflow.modules.api.chat.GenerateRequest;
import com.privateflow.modules.api.chat.RegenerateRequest;
import com.privateflow.modules.api.chat.SendConfirmRequest;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.Suggestion;
import com.privateflow.modules.supervision.SupervisionConfig;
import com.privateflow.modules.supervision.SupervisionEventService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatControllerTest {

  private ChatOrchestrationService service;
  private SupervisionEventService supervisionEventService;
  private RecognitionJobService recognitionJobService;
  private SupervisionConfig supervisionConfig;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(ChatOrchestrationService.class);
    supervisionEventService = org.mockito.Mockito.mock(SupervisionEventService.class);
    recognitionJobService = org.mockito.Mockito.mock(RecognitionJobService.class);
    supervisionConfig = org.mockito.Mockito.mock(SupervisionConfig.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new ChatController(
            service,
            supervisionEventService,
            recognitionJobService,
            supervisionConfig))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
  }

  @Test
  void recognizeBindsRequestAndReturnsSkillPayload() throws Exception {
    when(service.recognize(any())).thenReturn(response("13800000000", "Alice", false));

    mockMvc.perform(post("/api/v1/chat/recognize")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"textMessage\":\"hello\",\"customerIdentifier\":\"Alice\",\"leadType\":\"TUAN_GOU\",\"sourceTable\":\"crm\",\"rawMessages\":[{\"role\":\"customer\",\"text\":\"hello\",\"timestamp\":\"12:00\"}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.phone").value("13800000000"))
        .andExpect(jsonPath("$.data.skill.suggestions[0].text").value("Reply A"))
        .andExpect(jsonPath("$.data.replySource.source").value("SKILL"));

    ArgumentCaptor<ChatRecognizeRequest> captor = ArgumentCaptor.forClass(ChatRecognizeRequest.class);
    verify(service).recognize(captor.capture());
    org.junit.jupiter.api.Assertions.assertEquals("hello", captor.getValue().textMessage());
    org.junit.jupiter.api.Assertions.assertEquals("Alice", captor.getValue().customerIdentifier());
    org.junit.jupiter.api.Assertions.assertEquals(1, captor.getValue().rawMessages().size());
  }

  @Test
  void recognitionJobEndpointsSubmitPollAndCancelOnlyTheCallerJob() throws Exception {
    AuthUser caller = new AuthUser("SYSTEM", "System", Role.KEEPER, null);
    AuthContext.set(caller);
    RecognitionJobView submitted = recognitionJob(RecognitionJobStatus.QUEUED);
    RecognitionJobView ready = recognitionJob(RecognitionJobStatus.READY);
    RecognitionJobView cancelled = recognitionJob(RecognitionJobStatus.CANCELLED);
    when(recognitionJobService.submit(org.mockito.ArgumentMatchers.eq(caller), any()))
        .thenReturn(submitted);
    when(recognitionJobService.getOwned("job-1", "SYSTEM")).thenReturn(ready);
    when(recognitionJobService.cancelOwned("job-1", "SYSTEM")).thenReturn(cancelled);

    try {
      mockMvc.perform(post("/api/v1/chat/recognition-jobs")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"imageBase64\":\"c2NyZWVuc2hvdA==\",\"replySessionId\":\"reply-1\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.jobId").value("job-1"))
          .andExpect(jsonPath("$.data.status").value("QUEUED"));

      mockMvc.perform(get("/api/v1/chat/recognition-jobs/job-1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.status").value("READY"));

      mockMvc.perform(post("/api/v1/chat/recognition-jobs/job-1/cancel"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.status").value("CANCELLED"));

      verify(recognitionJobService).submit(org.mockito.ArgumentMatchers.eq(caller), any());
      verify(recognitionJobService).getOwned("job-1", "SYSTEM");
      verify(recognitionJobService).cancelOwned("job-1", "SYSTEM");
    } finally {
      AuthContext.clear();
    }
  }

  @Test
  void recognitionJobCustomerSelectionContinuesOnlyTheCallersWaitingTask() throws Exception {
    AuthUser caller = new AuthUser("SYSTEM", "System", Role.KEEPER, null);
    AuthContext.set(caller);
    when(recognitionJobService.selectCustomer("job-1", "SYSTEM", 42L))
        .thenReturn(response("13800000000", "Alice", false));

    try {
      mockMvc.perform(post("/api/v1/chat/recognition-jobs/job-1/select-customer")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"customerId\":42}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.phone").value("13800000000"));

      verify(recognitionJobService).selectCustomer("job-1", "SYSTEM", 42L);
    } finally {
      AuthContext.clear();
    }
  }

  @Test
  void taskRuntimeConfigIsReadOnlyAndDoesNotExposeAnEmployeeConfigurationMutation() throws Exception {
    when(supervisionConfig.unfinishedTaskCap()).thenReturn(20);
    when(supervisionConfig.recentTaskDisplayCap()).thenReturn(30);

    mockMvc.perform(get("/api/v1/chat/task-runtime-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.unfinishedTaskCap").value(20))
        .andExpect(jsonPath("$.data.recentTaskDisplayCap").value(30));
  }

  @Test
  void generateBindsRequest() throws Exception {
    when(service.generate(any())).thenReturn(response("13800000000", "Alice", false));

    mockMvc.perform(post("/api/v1/chat/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phone\":\"13800000000\",\"scene\":\"OPENING\",\"clientMessage\":\"Need help\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nickname").value("Alice"));

    ArgumentCaptor<GenerateRequest> captor = ArgumentCaptor.forClass(GenerateRequest.class);
    verify(service).generate(captor.capture());
    org.junit.jupiter.api.Assertions.assertEquals("OPENING", captor.getValue().scene());
  }

  @Test
  void regenerateReturnsWarningWhenServiceProvidesOne() throws Exception {
    when(service.regenerate(any())).thenReturn(new ChatResponse(
        "13800000000",
        null,
        false,
        null,
        new SkillResponse(List.of(new Suggestion("Reply B", "comfort", "retry")), null, null, null),
        "too many retries",
        ChatReplySource.skill()));

    mockMvc.perform(post("/api/v1/chat/regenerate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phone\":\"13800000000\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.warning").value("too many retries"))
        .andExpect(jsonPath("$.data.skill.suggestions[0].text").value("Reply B"));

    ArgumentCaptor<RegenerateRequest> captor = ArgumentCaptor.forClass(RegenerateRequest.class);
    verify(service).regenerate(captor.capture());
    org.junit.jupiter.api.Assertions.assertEquals("13800000000", captor.getValue().phone());
  }

  @Test
  void sendConfirmBindsRequestAndReturnsAcceptedPayload() throws Exception {
    when(service.sendConfirm(any())).thenReturn(Map.of("accepted", true));

    mockMvc.perform(post("/api/v1/chat/send-confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"confirmationId\":\"confirm-1\",\"phone\":\"13800000000\",\"nickname\":\"Alice\",\"isNewCustomer\":false,\"sourceTable\":\"crm\",\"leadType\":\"TUAN_GOU\",\"sentText\":\"Reply A\",\"selectedDirection\":\"comfort\",\"rawMessages\":[{\"role\":\"staff\",\"text\":\"Reply A\",\"timestamp\":\"12:01\"}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accepted").value(true));

    ArgumentCaptor<SendConfirmRequest> captor = ArgumentCaptor.forClass(SendConfirmRequest.class);
    verify(service).sendConfirm(captor.capture());
    org.junit.jupiter.api.Assertions.assertEquals("Reply A", captor.getValue().sentText());
    org.junit.jupiter.api.Assertions.assertEquals("confirm-1", captor.getValue().confirmationId());
    org.junit.jupiter.api.Assertions.assertEquals("comfort", captor.getValue().selectedDirection());
  }

  @Test
  void aiUsageRecordsCopiedReplyWithoutCallingSendConfirmFlow() throws Exception {
    when(supervisionEventService.recordAiUsage(any())).thenReturn(Map.of(
        "recorded", true,
        "semantic", "COPIED_AI_REPLY"));

    mockMvc.perform(post("/api/v1/chat/ai-usage")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phone\":\"18800001111\",\"taskId\":\"task-1\",\"replySessionId\":\"reply-session-1\",\"replySource\":\"LLM\",\"copiedText\":\"Reply A\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.recorded").value(true))
        .andExpect(jsonPath("$.data.semantic").value("COPIED_AI_REPLY"));

    ArgumentCaptor<AiUsageRequest> captor = ArgumentCaptor.forClass(AiUsageRequest.class);
    verify(supervisionEventService).recordAiUsage(captor.capture());
    org.junit.jupiter.api.Assertions.assertEquals("Reply A", captor.getValue().copiedText());
    org.junit.jupiter.api.Assertions.assertEquals("LLM", captor.getValue().replySource());
    org.mockito.Mockito.verifyNoInteractions(service);
  }

  @Test
  void serviceBadRequestMapsToStandardBody() throws Exception {
    when(service.generate(any())).thenThrow(new ApiException(ApiErrorCodes.BAD_REQUEST, "phone is required"));

    mockMvc.perform(post("/api/v1/chat/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  @Test
  void imageRecognitionFailureMapsToStandardClientErrorBody() throws Exception {
    when(service.recognize(any())).thenThrow(new ApiException("30-10001", "图片识别失败，请使用文字通道后重新生成回复"));

    mockMvc.perform(post("/api/v1/chat/recognize")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"imageBase64\":\"bad-image\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value("30-10001"))
        .andExpect(jsonPath("$.message").value("图片识别失败，请使用文字通道后重新生成回复"));
  }

  private ChatResponse response(String phone, String nickname, boolean needsCustomerIdentifier) {
    return new ChatResponse(
        phone,
        nickname,
        needsCustomerIdentifier,
        null,
        new SkillResponse(List.of(new Suggestion("Reply A", "comfort", "reason")), null, null, null),
        null,
        ChatReplySource.skill());
  }

  private RecognitionJobView recognitionJob(RecognitionJobStatus status) {
    return new RecognitionJobView(
        "job-1",
        "reply-1",
        status,
        null,
        status == RecognitionJobStatus.READY ? response("18800001111", "Alice", false) : null,
        Instant.parse("2026-07-24T10:00:00Z"),
        Instant.parse("2026-07-24T10:00:00Z"));
  }
}
