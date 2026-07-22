package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.match.Confidence;
import com.privateflow.modules.match.CustomerSummary;
import com.privateflow.modules.match.service.CustomerSummaryMapper;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.Suggestion;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingReplyTaskServiceTest {

  private static final String USERNAME = "keeper-1";

  @Mock
  private PendingReplyTaskRepository repository;
  @Mock
  private ChatTaskConfig config;
  @Mock
  private CustomerQueryService customerQueryService;
  @Mock
  private CustomerAccessService customerAccessService;
  @Mock
  private CustomerSummaryMapper customerSummaryMapper;

  private PendingReplyTaskService service;

  @BeforeEach
  void setUp() {
    service = new PendingReplyTaskService(
        repository,
        config,
        customerQueryService,
        customerAccessService,
        customerSummaryMapper);
  }

  @Test
  void listRecoverableRunsRecoveryAndOnlyShowsCurrentAccessibleCandidates() {
    stubRecoveryTimeout();
    PendingReplyTask waiting = task(
        "task-waiting",
        PendingReplyTaskStatus.WAITING_CUSTOMER,
        List.of("18800001111", "18800002222", "18800003333"),
        null);
    Customer allowed = customer("18800001111", "allowed");
    Customer inaccessible = customer("18800003333", "inaccessible");
    CustomerSummary summary = new CustomerSummary(
        "188****1111",
        "18800001111",
        "allowed",
        "WECHAT",
        "TUAN_GOU",
        USERNAME,
        null,
        null,
        Confidence.HIGH);
    when(repository.findActiveOwned(eq(USERNAME), any(LocalDateTime.class))).thenReturn(List.of(waiting));
    when(customerQueryService.getByPhone("18800001111")).thenReturn(allowed);
    when(customerQueryService.getByPhone("18800002222")).thenReturn(null);
    when(customerQueryService.getByPhone("18800003333")).thenReturn(inaccessible);
    when(customerAccessService.canAccess(allowed)).thenReturn(true);
    when(customerAccessService.canAccess(inaccessible)).thenReturn(false);
    when(customerSummaryMapper.toSummary(allowed, Confidence.HIGH)).thenReturn(summary);

    List<PendingReplyTaskView> views = service.listRecoverable(USERNAME);

    assertThat(views).singleElement().satisfies(view -> {
      assertThat(view.status()).isEqualTo(PendingReplyTaskStatus.WAITING_CUSTOMER);
      assertThat(view.candidates()).containsExactly(summary);
      assertThat(view.response()).isNull();
    });
    InOrder order = org.mockito.Mockito.inOrder(repository);
    order.verify(repository).recoverExpiredAndStalledTasks(
        any(LocalDateTime.class),
        eq(120),
        eq(Set.of()));
    order.verify(repository).findActiveOwned(eq(USERNAME), any(LocalDateTime.class));
    verify(repository, never()).claim(any(), any(), any());
    verify(repository, never()).markReady(any(), any(), any());
  }

  @Test
  void getRecoverableReturnsSavedReadyResponseWithoutRebuildingCandidates() {
    stubRecoveryTimeout();
    PendingReplyTask ready = task(
        "task-ready",
        PendingReplyTaskStatus.READY,
        List.of("18800001111"),
        "18800001111");
    ChatResponse savedResponse = new ChatResponse(
        "18800001111",
        "allowed",
        false,
        null,
        new SkillResponse(List.of(new Suggestion("saved reply", "NEXT_STEP", "")), null, null, null),
        null,
        ChatReplySource.skill());
    when(repository.findOwned(ready.taskId(), USERNAME)).thenReturn(Optional.of(ready));
    when(repository.readResult(ready)).thenReturn(savedResponse);

    PendingReplyTaskView view = service.getRecoverable(ready.taskId(), USERNAME);

    assertThat(view.status()).isEqualTo(PendingReplyTaskStatus.READY);
    assertThat(view.candidates()).isEmpty();
    assertThat(view.response()).isSameAs(savedResponse);
    verify(repository).readResult(ready);
    verifyNoInteractions(customerQueryService, customerAccessService, customerSummaryMapper);
  }

  @Test
  void recoveryExcludesOnlyCurrentlyActiveLocalGenerations() {
    stubRecoveryTimeout();
    PendingReplyTask generating = task(
        "task-read",
        PendingReplyTaskStatus.GENERATING,
        List.of("18800001111"),
        "18800001111");
    when(repository.findActiveOwned(eq(USERNAME), any(LocalDateTime.class))).thenReturn(List.of());
    when(repository.findOwned("task-read", USERNAME)).thenReturn(Optional.of(generating));

    service.beginGeneration("task-active");
    service.listRecoverable(USERNAME);

    verify(repository).recoverExpiredAndStalledTasks(
        any(LocalDateTime.class),
        eq(120),
        eq(Set.of("task-active")));

    service.endGeneration("task-active");
    service.getRecoverable("task-read", USERNAME);

    verify(repository).recoverExpiredAndStalledTasks(
        any(LocalDateTime.class),
        eq(120),
        eq(Set.of()));
  }

  @Test
  void claimRetryOnlyClaimsThePreviouslyFailedSelectedCustomer() {
    stubRecoveryTimeout();
    PendingReplyTask failed = task(
        "task-failed",
        PendingReplyTaskStatus.FAILED,
        List.of("18800001111"),
        "18800001111");
    PendingReplyTask generating = task(
        "task-failed",
        PendingReplyTaskStatus.GENERATING,
        List.of("18800001111"),
        "18800001111");
    when(repository.findOwned(failed.taskId(), USERNAME))
        .thenReturn(Optional.of(failed), Optional.of(generating));
    when(repository.claimRetry(failed.taskId(), USERNAME, "18800001111")).thenReturn(true);

    PendingReplyTask claimed = service.claimRetry(failed.taskId(), USERNAME);

    assertThat(claimed).isEqualTo(generating);
    verify(repository).claimRetry(failed.taskId(), USERNAME, "18800001111");
  }

  @Test
  void claimRetryRejectsAnyStatusOtherThanFailed() {
    stubRecoveryTimeout();
    PendingReplyTask waiting = task(
        "task-waiting",
        PendingReplyTaskStatus.WAITING_CUSTOMER,
        List.of("18800001111"),
        null);
    when(repository.findOwned(waiting.taskId(), USERNAME)).thenReturn(Optional.of(waiting));

    assertThatThrownBy(() -> service.claimRetry(waiting.taskId(), USERNAME))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getErrorCode())
        .isEqualTo(ApiErrorCodes.CONFLICT);

    verify(repository, never()).claimRetry(eq(waiting.taskId()), eq(USERNAME), any());
  }

  @Test
  void claimForGenerationRejectsFailedTaskWithAnotherCandidatePhone() {
    PendingReplyTask failed = task(
        "task-failed",
        PendingReplyTaskStatus.FAILED,
        List.of("18800001111", "18800002222"),
        "18800001111");
    when(repository.claim(failed.taskId(), USERNAME, "18800002222")).thenReturn(false);

    assertThatThrownBy(() -> service.claimForGeneration(failed.taskId(), USERNAME, "18800002222"))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getErrorCode())
        .isEqualTo(ApiErrorCodes.CONFLICT);

    verify(repository).claim(failed.taskId(), USERNAME, "18800002222");
  }

  @Test
  void cancelMovesWaitingTaskToCancelledView() {
    stubRecoveryTimeout();
    PendingReplyTask waiting = task(
        "task-waiting",
        PendingReplyTaskStatus.WAITING_CUSTOMER,
        List.of("18800001111"),
        null);
    PendingReplyTask cancelled = task(
        "task-waiting",
        PendingReplyTaskStatus.CANCELLED,
        List.of("18800001111"),
        null);
    when(repository.findOwned(waiting.taskId(), USERNAME))
        .thenReturn(Optional.of(waiting), Optional.of(cancelled));
    when(repository.cancel(waiting.taskId(), USERNAME)).thenReturn(true);

    PendingReplyTaskView view = service.cancel(waiting.taskId(), USERNAME);

    assertThat(view.status()).isEqualTo(PendingReplyTaskStatus.CANCELLED);
    assertThat(view.candidates()).isEmpty();
    verify(repository).cancel(waiting.taskId(), USERNAME);
  }

  @Test
  void cancelRejectsGeneratingTaskBeforeRepositoryUpdate() {
    stubRecoveryTimeout();
    PendingReplyTask generating = task(
        "task-generating",
        PendingReplyTaskStatus.GENERATING,
        List.of("18800001111"),
        "18800001111");
    when(repository.findOwned(generating.taskId(), USERNAME)).thenReturn(Optional.of(generating));

    assertThatThrownBy(() -> service.cancel(generating.taskId(), USERNAME))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getErrorCode())
        .isEqualTo(ApiErrorCodes.CONFLICT);

    verify(repository, never()).cancel(any(), any());
  }

  private PendingReplyTask task(
      String taskId,
      PendingReplyTaskStatus status,
      List<String> candidatePhones,
      String selectedPhone) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
    return new PendingReplyTask(
        1L,
        taskId,
        "reply-session-1",
        USERNAME,
        status,
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        candidatePhones,
        selectedPhone,
        null,
        null,
        status == PendingReplyTaskStatus.GENERATING ? now.minusMinutes(1) : null,
        now.plusHours(1),
        now.minusMinutes(2),
        now.minusMinutes(1));
  }

  private void stubRecoveryTimeout() {
    when(config.pendingReplyGeneratingTimeoutSeconds()).thenReturn(120);
  }

  private Customer customer(String phone, String nickname) {
    Customer customer = new Customer();
    customer.setPhone(phone);
    customer.setNickname(nickname);
    customer.setAssignedKeeper(USERNAME);
    customer.setLeadType("TUAN_GOU");
    return customer;
  }
}
