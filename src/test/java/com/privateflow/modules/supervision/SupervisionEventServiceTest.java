package com.privateflow.modules.supervision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.chat.AiUsageRequest;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.service.CustomerAccessService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SupervisionEventServiceTest {

  private final CustomerRepository customerRepository = mock(CustomerRepository.class);
  private final CustomerAccessService customerAccessService = mock(CustomerAccessService.class);
  private final SupervisionEventRepository eventRepository = mock(SupervisionEventRepository.class);
  private final SupervisionEventService service = new SupervisionEventService(
      customerRepository,
      customerAccessService,
      eventRepository,
      Clock.fixed(Instant.parse("2026-07-23T02:30:00Z"), ZoneOffset.UTC));

  @AfterEach
  void clearAuthContext() {
    AuthContext.clear();
  }

  @Test
  void recordsCopiedReplyWithAuthenticatedOperatorAndCustomerSnapshotOnly() {
    Customer customer = customer();
    when(customerRepository.findByPhone("18800001111")).thenReturn(Optional.of(customer));
    when(customerAccessService.canAccess(customer)).thenReturn(true);
    AuthContext.set(new AuthUser("keeper-auth", "\u7ba1\u5bb6", Role.KEEPER, null));

    Map<String, Object> result = service.recordAiUsage(new AiUsageRequest(
        "18800001111",
        "task-1",
        "reply-session-1",
        "LLM",
        "\u60a8\u597d\uff0c\u6211\u5148\u4e3a\u60a8\u68b3\u7406\u4e00\u4e0b\u3002"));

    assertThat(result).containsEntry("recorded", true)
        .containsEntry("semantic", "COPIED_AI_REPLY");
    ArgumentCaptor<SupervisionEventCommand> captor =
        ArgumentCaptor.forClass(SupervisionEventCommand.class);
    verify(eventRepository).insert(captor.capture());
    SupervisionEventCommand event = captor.getValue();
    assertThat(event.eventType()).isEqualTo(SupervisionEventType.REPLY_COPIED);
    assertThat(event.operatorUsername()).isEqualTo("keeper-auth");
    assertThat(event.customerPhone()).isEqualTo("18800001111");
    assertThat(event.channelCode()).isEqualTo("WECHAT");
    assertThat(event.leadSource()).isEqualTo("wecom_leads");
    assertThat(event.assignedKeeper()).isEqualTo("keeper-1");
    assertThat(event.taskId()).isEqualTo("task-1");
    assertThat(event.replySessionId()).isEqualTo("reply-session-1");
    assertThat(event.replySource()).isEqualTo("LLM");
    assertThat(event.generatedReplySnapshot()).isNull();
    assertThat(event.copiedReplySnapshot()).isEqualTo("\u60a8\u597d\uff0c\u6211\u5148\u4e3a\u60a8\u68b3\u7406\u4e00\u4e0b\u3002");
    assertThat(event.metadata()).containsEntry("customerId", 7L)
        .containsEntry("leadType", "MOM_CARE")
        .containsEntry("customerStage", "\u5f85\u8ddf\u8fdb");
    assertThat(event.occurredAt()).isEqualTo(LocalDateTime.of(2026, 7, 23, 2, 30));

    verify(customerRepository).findByPhone("18800001111");
    verify(customerAccessService).canAccess(customer);
    verifyNoMoreInteractions(customerRepository, customerAccessService);
  }

  @Test
  void rejectsInaccessibleCustomerWithoutWritingAnEvent() {
    Customer customer = customer();
    when(customerRepository.findByPhone("18800001111")).thenReturn(Optional.of(customer));
    when(customerAccessService.canAccess(customer)).thenReturn(false);
    AuthContext.set(new AuthUser("keeper-auth", "\u7ba1\u5bb6", Role.KEEPER, null));

    assertThatThrownBy(() -> service.recordAiUsage(request("\u53ef\u590d\u5236\u56de\u590d")))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getErrorCode())
        .isEqualTo(ApiErrorCodes.FORBIDDEN);

    verifyNoInteractions(eventRepository);
  }

  @Test
  void rejectsUnauthenticatedCopyWithoutReadingOrWritingCustomerData() {
    assertThatThrownBy(() -> service.recordAiUsage(request("\u53ef\u590d\u5236\u56de\u590d")))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getErrorCode())
        .isEqualTo(ApiErrorCodes.AUTH_FAILED);

    verifyNoInteractions(customerRepository, customerAccessService, eventRepository);
  }

  @Test
  void rejectsMissingCustomerWithoutWritingAnEvent() {
    when(customerRepository.findByPhone("18800001111")).thenReturn(Optional.empty());
    AuthContext.set(new AuthUser("keeper-auth", "\u7ba1\u5bb6", Role.KEEPER, null));

    assertThatThrownBy(() -> service.recordAiUsage(request("\u53ef\u590d\u5236\u56de\u590d")))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getErrorCode())
        .isEqualTo(ApiErrorCodes.BAD_REQUEST);

    verifyNoInteractions(customerAccessService, eventRepository);
  }

  @Test
  void rejectsMissingOrForgedReplySourceBeforeReadingCustomer() {
    AuthContext.set(new AuthUser("keeper-auth", "\u7ba1\u5bb6", Role.KEEPER, null));

    assertThatThrownBy(() -> service.recordAiUsage(new AiUsageRequest(
        "18800001111", "task-1", "reply-session-1", null, "\u53ef\u590d\u5236\u56de\u590d")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("replySource");
    assertThatThrownBy(() -> service.recordAiUsage(new AiUsageRequest(
        "18800001111", "task-1", "reply-session-1", "  ", "\u53ef\u590d\u5236\u56de\u590d")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("replySource");
    assertThatThrownBy(() -> service.recordAiUsage(new AiUsageRequest(
        "18800001111", "task-1", "reply-session-1", "LLM_WITH_SKILL", "\u53ef\u590d\u5236\u56de\u590d")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("replySource");
    assertThatThrownBy(() -> service.recordAiUsage(new AiUsageRequest(
        "18800001111", "task-1", "reply-session-1", "FORGED", "\u53ef\u590d\u5236\u56de\u590d")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("replySource");

    verifyNoInteractions(customerRepository, customerAccessService, eventRepository);
  }

  @Test
  void rejectsBlankOrOversizedCopiedTextBeforeReadingCustomer() {
    assertThatThrownBy(() -> service.recordAiUsage(request("  ")))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getErrorCode())
        .isEqualTo(ApiErrorCodes.BAD_REQUEST);
    assertThatThrownBy(() -> service.recordAiUsage(request("x".repeat(4001))))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getErrorCode())
        .isEqualTo(ApiErrorCodes.BAD_REQUEST);

    verifyNoInteractions(customerRepository, customerAccessService, eventRepository);
  }

  @Test
  void rejectsOversizedEventIdentifiersBeforeReadingCustomer() {
    AuthContext.set(new AuthUser("keeper-auth", "\u7ba1\u5bb6", Role.KEEPER, null));

    assertThatThrownBy(() -> service.recordAiUsage(new AiUsageRequest(
        "18800001111", "t".repeat(37), "reply-session-1", "LLM", "\u53ef\u590d\u5236\u56de\u590d")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("taskId");
    assertThatThrownBy(() -> service.recordAiUsage(new AiUsageRequest(
        "18800001111", "task-1", "s".repeat(81), "LLM", "\u53ef\u590d\u5236\u56de\u590d")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("replySessionId");
    assertThatThrownBy(() -> service.recordAiUsage(new AiUsageRequest(
        "18800001111", "task-1", "reply-session-1", "r".repeat(65), "\u53ef\u590d\u5236\u56de\u590d")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("replySource");

    verifyNoInteractions(customerRepository, customerAccessService, eventRepository);
  }

  @Test
  void replyCopiedEventDoesNotExposeGenericMetadataInput() {
    assertThat(SupervisionEventCommand.class.getConstructors()).isEmpty();
    assertThat(command().metadata()).containsExactlyInAnyOrderEntriesOf(Map.of(
        "customerId", 7L,
        "leadType", "MOM_CARE",
        "customerStage", "\u5f85\u8ddf\u8fdb"));
  }

  private AiUsageRequest request(String copiedText) {
    return new AiUsageRequest(
        "18800001111",
        "task-1",
        "reply-session-1",
        "LLM",
        copiedText);
  }

  private Customer customer() {
    Customer customer = new Customer();
    customer.setId(7L);
    customer.setPhone("18800001111");
    customer.setSourceChannel("WECHAT");
    customer.setSourceTable("wecom_leads");
    customer.setAssignedKeeper("keeper-1");
    customer.setLeadType("MOM_CARE");
    customer.setCustomerStage("\u5f85\u8ddf\u8fdb");
    return customer;
  }

  private SupervisionEventCommand command() {
    return SupervisionEventCommand.replyCopied(
        "06c1c2f4-0f8e-4c1f-9f44-12e7b0e80c61",
        "keeper-auth",
        "18800001111",
        "WECHAT",
        null,
        "wecom_leads",
        "keeper-1",
        null,
        "task-1",
        "reply-session-1",
        "LLM",
        null,
        null,
        "\u53ef\u590d\u5236\u56de\u590d",
        7L,
        "MOM_CARE",
        "\u5f85\u8ddf\u8fdb",
        LocalDateTime.of(2026, 7, 23, 2, 30));
  }
}
