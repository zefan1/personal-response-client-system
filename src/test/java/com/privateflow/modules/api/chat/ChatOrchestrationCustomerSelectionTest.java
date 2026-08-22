package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.image.ImageRecognitionService;
import com.privateflow.modules.llm.FollowupAnalysisRetryService;
import com.privateflow.modules.llm.LlmFollowupAnalysisService;
import com.privateflow.modules.llm.LlmReplyGenerationService;
import com.privateflow.modules.match.Confidence;
import com.privateflow.modules.match.CustomerMatchService;
import com.privateflow.modules.match.CustomerMatchException;
import com.privateflow.modules.match.CustomerMatchErrorCodes;
import com.privateflow.modules.match.CustomerSummary;
import com.privateflow.modules.match.MatchResult;
import com.privateflow.modules.match.MatchType;
import com.privateflow.modules.profile.service.FollowupAnalysisFieldMerger;
import com.privateflow.modules.profile.service.FollowupConfirmationService;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import com.privateflow.modules.skill.SkillGatewayService;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.Suggestion;
import com.privateflow.modules.supervision.SupervisionEventService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ChatOrchestrationCustomerSelectionTest {

  @Test
  void multipleNicknameCandidatesWaitForEmployeeSelectionBeforeAnyArchiveOrReplyGeneration() {
    CustomerMatchService matchService = mock(CustomerMatchService.class);
    CustomerQueryService customerQueryService = mock(CustomerQueryService.class);
    CustomerAccessService accessService = mock(CustomerAccessService.class);
    SkillGatewayService skillGatewayService = mock(SkillGatewayService.class);
    RecognitionCommunicationArchiveService archiveService = mock(RecognitionCommunicationArchiveService.class);
    Customer first = customer("13800000001");
    Customer second = customer("13800000002");
    Customer incorrectlyCreated = customer("13800000003");
    when(matchService.match(any())).thenReturn(new MatchResult(
        MatchType.MULTIPLE,
        List.of(summary("13800000001"), summary("13800000002")),
        2));
    when(customerQueryService.getByPhone("13800000001")).thenReturn(first);
    when(customerQueryService.getByPhone("13800000002")).thenReturn(second);
    when(accessService.canAccess(first)).thenReturn(true);
    when(accessService.canAccess(second)).thenReturn(true);
    when(accessService.canAccess(incorrectlyCreated)).thenReturn(true);
    when(archiveService.createRecognitionCustomer(any(), any())).thenReturn(incorrectlyCreated);
    when(skillGatewayService.generateReplies(any())).thenReturn(new SkillResponse(
        List.of(new Suggestion("错误路径不应生成", "NEXT_STEP", "test")), null, null, null));

    ChatOrchestrationService service = new ChatOrchestrationService(
        mock(ImageRecognitionService.class),
        matchService,
        skillGatewayService,
        customerQueryService,
        accessService,
        mock(ReplyTagSnapshotBuilder.class),
        mock(RequestContextStore.class),
        mock(ApplicationEventPublisher.class),
        mock(AuditLogger.class),
        mock(SkillConfigProvider.class),
        llmReplyServiceReturningNoReply(),
        mock(LlmFollowupAnalysisService.class),
        mock(FollowupAnalysisFieldMerger.class),
        mock(FollowupAnalysisRetryService.class),
        mock(FollowupConfirmationService.class),
        mock(SupervisionEventService.class),
        mock(SendConfirmationRepository.class),
        archiveService);

    ChatResponse response = service.recognize(new ChatRecognizeRequest(
        null, "我想了解产后修复", "小雨", "XIAN_SUO", "new-leads", List.of()));

    assertThat(response.match().matchType()).isEqualTo(MatchType.MULTIPLE);
    assertThat(response.skill()).isNull();
    verifyNoInteractions(archiveService, skillGatewayService);
  }

  @Test
  void matchingFailureIsReturnedAsAnErrorInsteadOfBeingTreatedAsNoCustomer() {
    CustomerMatchService matchService = mock(CustomerMatchService.class);
    when(matchService.match(any())).thenThrow(new CustomerMatchException(
        CustomerMatchErrorCodes.MATCH_FAILED, "客户匹配服务暂不可用"));
    ChatOrchestrationService service = new ChatOrchestrationService(
        mock(ImageRecognitionService.class),
        matchService,
        mock(SkillGatewayService.class),
        mock(CustomerQueryService.class),
        mock(CustomerAccessService.class),
        mock(ReplyTagSnapshotBuilder.class),
        mock(RequestContextStore.class),
        mock(ApplicationEventPublisher.class),
        mock(AuditLogger.class),
        mock(SkillConfigProvider.class),
        llmReplyServiceReturningNoReply(),
        mock(LlmFollowupAnalysisService.class),
        mock(FollowupAnalysisFieldMerger.class),
        mock(FollowupAnalysisRetryService.class),
        mock(FollowupConfirmationService.class),
        mock(SupervisionEventService.class),
        mock(SendConfirmationRepository.class),
        mock(RecognitionCommunicationArchiveService.class));

    assertThatThrownBy(() -> service.recognize(new ChatRecognizeRequest(
        null, "聊天内容", "小雨", "XIAN_SUO", "new-leads", List.of())))
        .isInstanceOf(ApiException.class)
        .hasMessage("客户匹配服务暂不可用");
  }

  private Customer customer(String phone) {
    Customer customer = new Customer();
    customer.setPhone(phone);
    return customer;
  }

  private CustomerSummary summary(String phone) {
    return new CustomerSummary(
        phone,
        phone,
        "小雨",
        "WECHAT",
        "XIAN_SUO",
        "keeper-a",
        null,
        "上海门店",
        Confidence.HIGH);
  }

  private LlmReplyGenerationService llmReplyServiceReturningNoReply() {
    LlmReplyGenerationService service = mock(LlmReplyGenerationService.class);
    when(service.tryGenerate(any(), any())).thenReturn(Optional.empty());
    return service;
  }
}
