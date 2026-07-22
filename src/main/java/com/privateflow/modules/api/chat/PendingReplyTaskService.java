package com.privateflow.modules.api.chat;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.match.Confidence;
import com.privateflow.modules.match.CustomerSummary;
import com.privateflow.modules.match.service.CustomerSummaryMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PendingReplyTaskService {

  private final PendingReplyTaskRepository repository;
  private final ChatTaskConfig config;
  private final CustomerQueryService customerQueryService;
  private final CustomerAccessService customerAccessService;
  private final CustomerSummaryMapper customerSummaryMapper;
  private final Set<String> activeGenerationTaskIds = ConcurrentHashMap.newKeySet();

  @Autowired
  public PendingReplyTaskService(
      PendingReplyTaskRepository repository,
      ChatTaskConfig config,
      CustomerQueryService customerQueryService,
      CustomerAccessService customerAccessService,
      CustomerSummaryMapper customerSummaryMapper) {
    this.repository = repository;
    this.config = config;
    this.customerQueryService = customerQueryService;
    this.customerAccessService = customerAccessService;
    this.customerSummaryMapper = customerSummaryMapper;
  }

  // Used by direct repository tests that only recover a persisted READY result.
  public PendingReplyTaskService(PendingReplyTaskRepository repository, ChatTaskConfig config) {
    this.repository = repository;
    this.config = config;
    this.customerQueryService = null;
    this.customerAccessService = null;
    this.customerSummaryMapper = null;
  }

  public PendingReplyTaskView createWaitingTask(PendingReplyTaskDraft draft) {
    PendingReplyTask task = repository.create(draft, config.pendingReplyTtlHours());
    return new PendingReplyTaskView(
        task.taskId(),
        task.replySessionId(),
        task.status(),
        draft.candidates() == null ? List.of() : draft.candidates(),
        task.selectedPhone(),
        null,
        task.errorCode(),
        task.expiresAt());
  }

  public PendingReplyTask claimForGeneration(String taskId, String username, String phone) {
    if (blank(taskId) || blank(username) || blank(phone)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "taskId and phone are required");
    }
    if (!repository.claim(taskId, username, phone)) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available");
    }
    PendingReplyTask task = repository.findOwned(taskId, username)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available"));
    if (task.status() != PendingReplyTaskStatus.GENERATING || !phone.equals(task.selectedPhone())) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available");
    }
    return task;
  }

  public void markReady(PendingReplyTask task, ChatResponse response) {
    if (task == null || response == null
        || !repository.markReady(task.taskId(), task.username(), response)) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available");
    }
  }

  public PendingReplyTaskView getRecoverable(String taskId, String username) {
    if (blank(taskId) || blank(username)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "taskId is required");
    }
    recoverTasks();
    PendingReplyTask task = repository.findOwned(taskId, username)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available"));
    if (!recoverable(task.status())) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available");
    }
    return view(task);
  }

  public List<PendingReplyTaskView> listRecoverable(String username) {
    if (blank(username)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "username is required");
    }
    recoverTasks();
    return repository.findActiveOwned(username, LocalDateTime.now()).stream()
        .map(this::view)
        .toList();
  }

  public PendingReplyTask claimRetry(String taskId, String username) {
    if (blank(taskId) || blank(username)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "taskId is required");
    }
    recoverTasks();
    PendingReplyTask failed = repository.findOwned(taskId, username)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available"));
    if (failed.status() != PendingReplyTaskStatus.FAILED || blank(failed.selectedPhone())) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available");
    }
    if (!repository.claimRetry(taskId, username, failed.selectedPhone())) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available");
    }
    PendingReplyTask claimed = repository.findOwned(taskId, username)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available"));
    if (claimed.status() != PendingReplyTaskStatus.GENERATING
        || !failed.selectedPhone().equals(claimed.selectedPhone())) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available");
    }
    return claimed;
  }

  public PendingReplyTaskView cancel(String taskId, String username) {
    if (blank(taskId) || blank(username)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "taskId is required");
    }
    recoverTasks();
    PendingReplyTask task = repository.findOwned(taskId, username)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available"));
    if (task.status() != PendingReplyTaskStatus.WAITING_CUSTOMER
        && task.status() != PendingReplyTaskStatus.FAILED) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available");
    }
    if (!repository.cancel(taskId, username)) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available");
    }
    PendingReplyTask cancelled = repository.findOwned(taskId, username)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available"));
    if (cancelled.status() != PendingReplyTaskStatus.CANCELLED) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available");
    }
    return view(cancelled);
  }

  public void releaseSelection(PendingReplyTask task) {
    if (task == null || !repository.releaseSelection(task.taskId(), task.username())) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available");
    }
  }

  public void markFailed(PendingReplyTask task, String publicErrorCode) {
    if (task == null || blank(publicErrorCode)
        || !repository.markFailed(task.taskId(), task.username(), publicErrorCode)) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available");
    }
  }

  public void beginGeneration(String taskId) {
    if (!blank(taskId)) {
      activeGenerationTaskIds.add(taskId);
    }
  }

  public void endGeneration(String taskId) {
    if (!blank(taskId)) {
      activeGenerationTaskIds.remove(taskId);
    }
  }

  private PendingReplyTaskView view(PendingReplyTask task) {
    List<CustomerSummary> candidates = switch (task.status()) {
      case WAITING_CUSTOMER, FAILED -> currentCandidates(task.candidatePhones());
      default -> List.of();
    };
    ChatResponse response = task.status() == PendingReplyTaskStatus.READY
        ? repository.readResult(task)
        : null;
    return new PendingReplyTaskView(
        task.taskId(),
        task.replySessionId(),
        task.status(),
        candidates,
        task.selectedPhone(),
        response,
        task.errorCode(),
        task.expiresAt());
  }

  private List<CustomerSummary> currentCandidates(List<String> candidatePhones) {
    if (candidatePhones == null || candidatePhones.isEmpty()
        || customerQueryService == null || customerAccessService == null || customerSummaryMapper == null) {
      return List.of();
    }
    List<CustomerSummary> candidates = new ArrayList<>();
    for (String phone : candidatePhones) {
      if (blank(phone)) {
        continue;
      }
      Customer customer = customerQueryService.getByPhone(phone);
      if (customer != null && customerAccessService.canAccess(customer)) {
        CustomerSummary summary = customerSummaryMapper.toSummary(customer, Confidence.HIGH);
        if (summary != null) {
          candidates.add(summary);
        }
      }
    }
    return List.copyOf(candidates);
  }

  private void recoverTasks() {
    int timeoutSeconds = Math.max(1, config.pendingReplyGeneratingTimeoutSeconds());
    repository.recoverExpiredAndStalledTasks(
        LocalDateTime.now(),
        timeoutSeconds,
        Set.copyOf(activeGenerationTaskIds));
  }

  private boolean recoverable(PendingReplyTaskStatus status) {
    return status == PendingReplyTaskStatus.WAITING_CUSTOMER
        || status == PendingReplyTaskStatus.GENERATING
        || status == PendingReplyTaskStatus.FAILED
        || status == PendingReplyTaskStatus.READY;
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
