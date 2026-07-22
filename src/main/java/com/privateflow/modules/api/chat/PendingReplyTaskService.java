package com.privateflow.modules.api.chat;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PendingReplyTaskService {

  private final PendingReplyTaskRepository repository;
  private final ChatTaskConfig config;

  public PendingReplyTaskService(PendingReplyTaskRepository repository, ChatTaskConfig config) {
    this.repository = repository;
    this.config = config;
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
    PendingReplyTask task = repository.findOwned(taskId, username)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available"));
    if (task.status() != PendingReplyTaskStatus.READY) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "reply task is no longer available");
    }
    return new PendingReplyTaskView(
        task.taskId(),
        task.replySessionId(),
        task.status(),
        List.of(),
        task.selectedPhone(),
        repository.readResult(task),
        task.errorCode(),
        task.expiresAt());
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

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
