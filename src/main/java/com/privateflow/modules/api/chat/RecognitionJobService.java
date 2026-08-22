package com.privateflow.modules.api.chat;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.image.ImageRecognitionException;
import com.privateflow.modules.image.processing.ImagePreprocessor;
import com.privateflow.modules.image.processing.ImageValidator;
import com.privateflow.modules.supervision.SupervisionConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Owns the global FIFO admission gate. Recognition itself is wired in Task 3; this boundary already
 * owns temporary image admission, status polling, cancellation, timeout cleanup, and slot release.
 */
@Service
public class RecognitionJobService {

  private final RecognitionJobRepository repository;
  private final TemporaryRecognitionImageStore imageStore;
  private final ImageValidator imageValidator;
  private final ImagePreprocessor imagePreprocessor;
  private final SupervisionConfig supervisionConfig;
  private final Executor executor;
  private final ChatOrchestrationService orchestrationService;
  private final ChatTaskConfig taskConfig;
  private final RecognitionJobRecoveryRepository recoveryRepository;
  private final Clock clock;
  private final Object monitor = new Object();

  @Autowired
  public RecognitionJobService(
      RecognitionJobRepository repository,
      TemporaryRecognitionImageStore imageStore,
      ImageValidator imageValidator,
      ImagePreprocessor imagePreprocessor,
      SupervisionConfig supervisionConfig,
      @Qualifier("chatRecognitionExecutor") Executor executor,
      ChatOrchestrationService orchestrationService,
      ChatTaskConfig taskConfig,
      RecognitionJobRecoveryRepository recoveryRepository) {
    this(
        repository,
        imageStore,
        imageValidator,
        imagePreprocessor,
        supervisionConfig,
        executor,
        orchestrationService,
        taskConfig,
        recoveryRepository,
        Clock.systemUTC());
  }

  // Kept package-visible for focused scheduling tests that do not execute an orchestration worker.
  RecognitionJobService(
      RecognitionJobRepository repository,
      TemporaryRecognitionImageStore imageStore,
      ImageValidator imageValidator,
      ImagePreprocessor imagePreprocessor,
      SupervisionConfig supervisionConfig,
      Executor executor,
      Clock clock) {
    this(
        repository,
        imageStore,
        imageValidator,
        imagePreprocessor,
        supervisionConfig,
        executor,
        null,
        null,
        null,
        clock);
  }

  RecognitionJobService(
      RecognitionJobRepository repository,
      TemporaryRecognitionImageStore imageStore,
      ImageValidator imageValidator,
      ImagePreprocessor imagePreprocessor,
      SupervisionConfig supervisionConfig,
      Executor executor,
      ChatOrchestrationService orchestrationService,
      ChatTaskConfig taskConfig,
      RecognitionJobRecoveryRepository recoveryRepository,
      Clock clock) {
    this.repository = repository;
    this.imageStore = imageStore;
    this.imageValidator = imageValidator;
    this.imagePreprocessor = imagePreprocessor;
    this.supervisionConfig = supervisionConfig;
    this.executor = executor;
    this.orchestrationService = orchestrationService;
    this.taskConfig = taskConfig;
    this.recoveryRepository = recoveryRepository;
    this.clock = clock;
  }

  public RecognitionJobView submit(String username, ChatRecognizeRequest request) {
    AuthUser current = AuthContext.current();
    AuthUser employee = current != null && username != null && username.equals(current.username())
        ? current
        : new AuthUser(username, username, Role.KEEPER, null);
    return submit(employee, request);
  }

  public RecognitionJobView submit(AuthUser employee, ChatRecognizeRequest request) {
    validateSubmission(employee, request);
    String username = employee.username();
    synchronized (monitor) {
      ensureEmployeeHasCapacity(username);
    }
    byte[] jpegBytes = preprocess(request.imageBase64());
    synchronized (monitor) {
      ensureEmployeeHasCapacity(username);
      String imageToken = storeImage(jpegBytes);
      RecognitionJob job = new RecognitionJob(
          UUID.randomUUID().toString(),
          username,
          employee,
          imageToken,
          withoutImagePayload(request),
          clock.instant());
      repository.save(job);
      persist(job);
      drainQueue();
      return job.view();
    }
  }

  public RecognitionJobView getOwned(String jobId, String username) {
    synchronized (monitor) {
      RecognitionJob active = repository.find(jobId).orElse(null);
      if (active != null) {
        assertOwner(active.username(), username);
        return active.view();
      }
      return recoveredOwned(jobId, username);
    }
  }

  public RecognitionJobView cancelOwned(String jobId, String username) {
    synchronized (monitor) {
      RecognitionJob job = owned(jobId, username);
      boolean deleteImmediately = job.cancel(clock.instant());
      persist(job);
      if (deleteImmediately) {
        imageStore.delete(job.imageToken());
        pruneTerminalHistory();
      }
      return job.view();
    }
  }

  public ChatResponse selectCustomer(String jobId, String username, Long customerId) {
    RecognitionJob job;
    ChatResponse waitingResponse;
    synchronized (monitor) {
      job = owned(jobId, username);
      waitingResponse = job.view().response();
      if (waitingResponse == null || !waitingResponse.awaitingCustomerSelection()) {
        throw new ApiException(ApiErrorCodes.CONFLICT, "recognition task is not waiting for customer selection");
      }
    }
    ChatResponse completed = orchestrationService.resolveSelectedCustomer(
        job.request(), waitingResponse.recognition(), waitingResponse.match(), customerId);
    synchronized (monitor) {
      if (!job.replaceResponse(completed, clock.instant())) {
        throw new ApiException(ApiErrorCodes.CONFLICT, "recognition task is no longer available");
      }
      persist(job);
    }
    return completed;
  }

  /** Called by the recognition worker when a reply-ready result is available. */
  void completeForWorker(String jobId, ChatResponse response, RecognitionJobStatus completedStatus) {
    if (completedStatus != RecognitionJobStatus.READY) {
      throw new IllegalArgumentException("recognition jobs can only complete as ready");
    }
    synchronized (monitor) {
      RecognitionJob job = repository.find(jobId).orElse(null);
      if (job == null || !job.complete(response, completedStatus, clock.instant())) {
        return;
      }
      persist(job);
      imageStore.delete(job.imageToken());
      pruneTerminalHistory();
      drainQueue();
    }
  }

  /** Called by the Task 3 worker for a recoverable public recognition failure. */
  void failForWorker(String jobId, String publicErrorCode) {
    synchronized (monitor) {
      RecognitionJob job = repository.find(jobId).orElse(null);
      if (job == null || !job.fail(publicErrorCode, clock.instant())) {
        return;
      }
      persist(job);
      imageStore.delete(job.imageToken());
      pruneTerminalHistory();
      drainQueue();
    }
  }

  @Scheduled(fixedDelay = 60_000)
  public void expireJobsMissingTheirTemporaryImage() {
    imageStore.cleanupExpired();
    synchronized (monitor) {
      for (RecognitionJob job : repository.activeJobs()) {
        if ((job.requiresTemporaryImage() && !imageStore.exists(job.imageToken()))
            || job.resultExpired(clock.instant(), taskRetention())) {
          if (!job.expire(clock.instant())) {
            continue;
          }
          persist(job);
          imageStore.delete(job.imageToken());
        }
      }
      pruneTerminalHistory();
      drainQueue();
    }
  }

  private void drainQueue() {
    while (repository.runningCount() < supervisionConfig.recognitionConcurrency()) {
      RecognitionJob next = repository.takeNextQueued().orElse(null);
      if (next == null) {
        return;
      }
      next.start(clock.instant());
      persist(next);
      try {
        executor.execute(() -> runWorker(next.jobId()));
      } catch (RejectedExecutionException ex) {
        next.fail("RECOGNITION_QUEUE_UNAVAILABLE", clock.instant());
        persist(next);
        imageStore.delete(next.imageToken());
        pruneTerminalHistory();
      }
    }
  }

  private void runWorker(String jobId) {
    RecognitionJob job;
    synchronized (monitor) {
      job = repository.find(jobId).orElse(null);
      if (job == null) {
        return;
      }
    }
    try {
      if (!isJobStillActive(jobId)) {
        return;
      }
      if (orchestrationService == null) {
        throw new IllegalStateException("recognition orchestration is unavailable");
      }
      byte[] jpegBytes = imageStore.read(job.imageToken());
      if (!isJobStillActive(jobId)) {
        return;
      }
      ChatResponse response = orchestrationService.recognizeForJob(
          job.request(), jpegBytes, job.authUser(), () -> isJobStillActive(jobId));
      completeFromRunningWorker(job, response, RecognitionJobStatus.READY);
    } catch (ApiException ex) {
      failFromRunningWorker(job, ex.getErrorCode());
    } catch (RuntimeException ex) {
      failFromRunningWorker(job, "RECOGNITION_PROCESSING_FAILED");
    } finally {
      imageStore.delete(job.imageToken());
      synchronized (monitor) {
        pruneTerminalHistory();
        drainQueue();
      }
    }
  }

  private void completeFromRunningWorker(
      RecognitionJob job, ChatResponse response, RecognitionJobStatus completedStatus) {
    synchronized (monitor) {
      job.complete(response, completedStatus, clock.instant());
      persist(job);
    }
  }

  private void failFromRunningWorker(RecognitionJob job, String publicErrorCode) {
    synchronized (monitor) {
      job.fail(publicErrorCode, clock.instant());
      persist(job);
    }
  }

  private boolean isJobStillActive(String jobId) {
    synchronized (monitor) {
      return repository.find(jobId)
          .map(RecognitionJob::runningAndActive)
          .orElse(false);
    }
  }

  private Duration taskRetention() {
    int hours = taskConfig == null ? 24 : taskConfig.pendingReplyTtlHours();
    return Duration.ofHours(Math.max(1, hours));
  }

  private void pruneTerminalHistory() {
    repository.pruneTerminalHistory(supervisionConfig.recentTaskDisplayCap());
    if (recoveryRepository != null) {
      recoveryRepository.deleteTerminalBefore(clock.instant().minus(taskRetention()));
    }
  }

  private RecognitionJob owned(String jobId, String username) {
    if (blank(jobId) || blank(username)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "recognition job is required");
    }
    RecognitionJob job = repository.find(jobId)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.CONFLICT, "recognition job is no longer available"));
    assertOwner(job.username(), username);
    return job;
  }

  private RecognitionJobView recoveredOwned(String jobId, String username) {
    if (blank(jobId) || blank(username)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "recognition job is required");
    }
    RecognitionJobRecoveryRepository.RecoveredRecognitionJob recovered = recoveryRepository == null
        ? null : recoveryRepository.find(jobId).orElse(null);
    if (recovered == null) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "recognition job is no longer available");
    }
    assertOwner(recovered.username(), username);
    return recovered.view();
  }

  private void assertOwner(String owner, String username) {
    if (!username.equals(owner)) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "无权操作该识图任务");
    }
  }

  private void persist(RecognitionJob job) {
    if (recoveryRepository != null) {
      recoveryRepository.save(job.view(), job.username());
    }
  }

  private void validateSubmission(AuthUser employee, ChatRecognizeRequest request) {
    if (employee == null || blank(employee.username()) || request == null || blank(request.imageBase64())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "识图任务需要聊天截图");
    }
  }

  private void ensureEmployeeHasCapacity(String username) {
    if (repository.unfinishedCount(username) >= supervisionConfig.unfinishedTaskCap()) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "回复任务已满，请先处理现有任务");
    }
  }

  private byte[] preprocess(String imageBase64) {
    try {
      byte[] original = Base64.getDecoder().decode(imageBase64);
      imageValidator.validate(original);
      return imagePreprocessor.preprocess(original);
    } catch (ImageRecognitionException ex) {
      throw new ApiException(ex.getErrorCode(), ex.getMessage());
    } catch (IllegalArgumentException ex) {
      throw new ApiException("30-10002", "图片格式不支持，请重新截图或使用 PNG/JPG");
    }
  }

  private String storeImage(byte[] jpegBytes) {
    try {
      return imageStore.put(jpegBytes);
    } catch (IllegalStateException ex) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "临时识图空间已满，请稍后重试");
    } catch (IllegalArgumentException ex) {
      throw new ApiException("30-10002", "图片格式不支持，请重新截图或使用 PNG/JPG");
    }
  }

  private ChatRecognizeRequest withoutImagePayload(ChatRecognizeRequest request) {
    return new ChatRecognizeRequest(
        null,
        request.textMessage(),
        request.customerIdentifier(),
        request.leadType(),
        request.sourceTable(),
        request.rawMessages() == null ? List.of() : List.copyOf(request.rawMessages()),
        request.replySessionId());
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
