package com.privateflow.modules.api.chat;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.image.ImageRecognitionException;
import com.privateflow.modules.image.processing.ImagePreprocessor;
import com.privateflow.modules.image.processing.ImageValidator;
import com.privateflow.modules.supervision.SupervisionConfig;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
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
  private final Clock clock;
  private final Object monitor = new Object();

  public RecognitionJobService(
      RecognitionJobRepository repository,
      TemporaryRecognitionImageStore imageStore,
      ImageValidator imageValidator,
      ImagePreprocessor imagePreprocessor,
      SupervisionConfig supervisionConfig,
      @Qualifier("chatRecognitionExecutor") Executor executor) {
    this(repository, imageStore, imageValidator, imagePreprocessor, supervisionConfig, executor,
        Clock.systemUTC());
  }

  RecognitionJobService(
      RecognitionJobRepository repository,
      TemporaryRecognitionImageStore imageStore,
      ImageValidator imageValidator,
      ImagePreprocessor imagePreprocessor,
      SupervisionConfig supervisionConfig,
      Executor executor,
      Clock clock) {
    this.repository = repository;
    this.imageStore = imageStore;
    this.imageValidator = imageValidator;
    this.imagePreprocessor = imagePreprocessor;
    this.supervisionConfig = supervisionConfig;
    this.executor = executor;
    this.clock = clock;
  }

  public RecognitionJobView submit(String username, ChatRecognizeRequest request) {
    validateSubmission(username, request);
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
          imageToken,
          withoutImagePayload(request),
          clock.instant());
      repository.save(job);
      drainQueue();
      return job.view();
    }
  }

  public RecognitionJobView getOwned(String jobId, String username) {
    synchronized (monitor) {
      return owned(jobId, username).view();
    }
  }

  public RecognitionJobView cancelOwned(String jobId, String username) {
    synchronized (monitor) {
      RecognitionJob job = owned(jobId, username);
      boolean deleteImmediately = job.cancel(clock.instant());
      if (deleteImmediately) {
        imageStore.delete(job.imageToken());
      }
      return job.view();
    }
  }

  /** Called by the Task 3 worker when it reaches a customer-ready or reply-ready result. */
  void completeForWorker(String jobId, ChatResponse response, RecognitionJobStatus completedStatus) {
    if (completedStatus != RecognitionJobStatus.READY
        && completedStatus != RecognitionJobStatus.WAITING_CUSTOMER) {
      throw new IllegalArgumentException("recognition jobs can only complete as ready or waiting customer");
    }
    synchronized (monitor) {
      RecognitionJob job = repository.find(jobId).orElse(null);
      if (job == null || !job.complete(response, completedStatus, clock.instant())) {
        return;
      }
      imageStore.delete(job.imageToken());
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
      imageStore.delete(job.imageToken());
      drainQueue();
    }
  }

  @Scheduled(fixedDelay = 60_000)
  public void expireJobsMissingTheirTemporaryImage() {
    imageStore.cleanupExpired();
    synchronized (monitor) {
      for (RecognitionJob job : repository.activeJobs()) {
        if (!imageStore.exists(job.imageToken()) && job.expire(clock.instant())) {
          imageStore.delete(job.imageToken());
        }
      }
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
      try {
        executor.execute(() -> runWorker(next.jobId()));
      } catch (RejectedExecutionException ex) {
        next.fail("RECOGNITION_QUEUE_UNAVAILABLE", clock.instant());
        imageStore.delete(next.imageToken());
      }
    }
  }

  private void runWorker(String jobId) {
    // Task 3 supplies recognition execution and always finishes through completeForWorker/failForWorker.
  }

  private RecognitionJob owned(String jobId, String username) {
    if (blank(jobId) || blank(username)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "recognition job is required");
    }
    RecognitionJob job = repository.find(jobId)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.CONFLICT, "recognition job is no longer available"));
    if (!username.equals(job.username())) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "无权操作该识图任务");
    }
    return job;
  }

  private void validateSubmission(String username, ChatRecognizeRequest request) {
    if (blank(username) || request == null || blank(request.imageBase64())) {
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
