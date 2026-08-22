package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.image.processing.ImagePreprocessor;
import com.privateflow.modules.image.processing.ImageValidator;
import com.privateflow.modules.supervision.SupervisionConfig;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecognitionJobServiceTest {

  private final RecordingExecutor executor = new RecordingExecutor();
  private final TemporaryRecognitionImageStore imageStore = mock(TemporaryRecognitionImageStore.class);
  private final ImageValidator imageValidator = mock(ImageValidator.class);
  private final ImagePreprocessor imagePreprocessor = mock(ImagePreprocessor.class);
  private final SupervisionConfig supervisionConfig = mock(SupervisionConfig.class);
  private final ChatOrchestrationService orchestrationService = mock(ChatOrchestrationService.class);
  private final ChatTaskConfig taskConfig = mock(ChatTaskConfig.class);
  private RecognitionJobService service;

  @BeforeEach
  void setUp() {
    when(supervisionConfig.recognitionConcurrency()).thenReturn(4);
    when(supervisionConfig.unfinishedTaskCap()).thenReturn(20);
    when(supervisionConfig.recentTaskDisplayCap()).thenReturn(30);
    when(taskConfig.pendingReplyTtlHours()).thenReturn(24);
    when(imagePreprocessor.preprocess(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(imageStore.put(any())).thenAnswer(invocation -> "image-" + executor.nextImageToken());
    when(orchestrationService.recognizeForJob(any(), any(), any(), any())).thenReturn(response());
    service = new RecognitionJobService(
        new RecognitionJobRepository(),
        imageStore,
        imageValidator,
        imagePreprocessor,
        supervisionConfig,
        executor,
        orchestrationService,
        taskConfig,
        null,
        Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void runsOnlyConfiguredConcurrentJobsAndStartsTheNextJobInFifoOrder() {
    List<RecognitionJobView> jobs = new ArrayList<>();
    for (int index = 0; index < 5; index++) {
      jobs.add(service.submit("keeper-a", imageRequest("reply-" + index)));
    }

    assertThat(jobs.subList(0, 4)).allSatisfy(job ->
        assertThat(job.status()).isEqualTo(RecognitionJobStatus.RECOGNIZING));
    assertThat(jobs.get(4).status()).isEqualTo(RecognitionJobStatus.QUEUED);
    assertThat(executor.submissions()).hasSize(4);

    service.completeForWorker(jobs.get(0).jobId(), response(), RecognitionJobStatus.READY);

    assertThat(service.getOwned(jobs.get(4).jobId(), "keeper-a").status())
        .isEqualTo(RecognitionJobStatus.RECOGNIZING);
    assertThat(executor.submissions()).hasSize(5);
  }

  @Test
  void rejectsNewJobsWhenTheEmployeeHasReachedTheirUnfinishedTaskCap() {
    when(supervisionConfig.unfinishedTaskCap()).thenReturn(2);
    service.submit("keeper-a", imageRequest("reply-1"));
    service.submit("keeper-a", imageRequest("reply-2"));

    assertThatThrownBy(() -> service.submit("keeper-a", imageRequest("reply-3")))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
            .isEqualTo(ApiErrorCodes.CONFLICT))
        .hasMessageContaining("回复任务已满");
    verify(imagePreprocessor, never()).preprocess("reply-3".getBytes(StandardCharsets.UTF_8));
    verify(imageStore, never()).put("reply-3".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void deniesAccessToAJobOwnedByAnotherEmployee() {
    RecognitionJobView job = service.submit("keeper-a", imageRequest("reply-1"));

    assertThatThrownBy(() -> service.getOwned(job.jobId(), "keeper-b"))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
            .isEqualTo(ApiErrorCodes.FORBIDDEN));
  }

  @Test
  void returnsThePersistedRestartFailureToTheOriginalTaskOwnerAfterRestart() {
    RecognitionJobRecoveryRepository recovery = mock(RecognitionJobRecoveryRepository.class);
    Instant createdAt = Instant.parse("2026-07-24T10:00:00Z");
    when(recovery.find("restarted-job")).thenReturn(Optional.of(
        new RecognitionJobRecoveryRepository.RecoveredRecognitionJob(
            "restarted-job",
            "keeper-a",
            "reply-1",
            RecognitionJobStatus.FAILED,
            RecognitionJobRecoveryRepository.BACKEND_RESTARTED,
            createdAt,
            createdAt.plusSeconds(1))));
    service = new RecognitionJobService(
        new RecognitionJobRepository(),
        imageStore,
        imageValidator,
        imagePreprocessor,
        supervisionConfig,
        executor,
        orchestrationService,
        taskConfig,
        recovery,
        Clock.fixed(createdAt.plusSeconds(2), ZoneOffset.UTC));

    RecognitionJobView job = service.getOwned("restarted-job", "keeper-a");

    assertThat(job.status()).isEqualTo(RecognitionJobStatus.FAILED);
    assertThat(job.errorCode()).isEqualTo(RecognitionJobRecoveryRepository.BACKEND_RESTARTED);
    assertThat(job.response()).isNull();
  }

  @Test
  void cancellingAQueuedJobDeletesItsTemporaryImageImmediately() {
    when(supervisionConfig.recognitionConcurrency()).thenReturn(1);
    RecognitionJobView running = service.submit("keeper-a", imageRequest("reply-1"));
    RecognitionJobView queued = service.submit("keeper-a", imageRequest("reply-2"));

    RecognitionJobView cancelled = service.cancelOwned(queued.jobId(), "keeper-a");

    assertThat(running.status()).isEqualTo(RecognitionJobStatus.RECOGNIZING);
    assertThat(cancelled.status()).isEqualTo(RecognitionJobStatus.CANCELLED);
    verify(imageStore).delete("image-2");
  }

  @Test
  void cancellingARunningJobWaitsForWorkerCompletionBeforeDeletingItsImage() {
    RecognitionJobView running = service.submit("keeper-a", imageRequest("reply-1"));

    RecognitionJobView cancelled = service.cancelOwned(running.jobId(), "keeper-a");

    assertThat(cancelled.status()).isEqualTo(RecognitionJobStatus.CANCELLED);
    verify(imageStore, never()).delete("image-1");

    service.completeForWorker(running.jobId(), response(), RecognitionJobStatus.READY);

    assertThat(service.getOwned(running.jobId(), "keeper-a").status())
        .isEqualTo(RecognitionJobStatus.CANCELLED);
    verify(imageStore).delete("image-1");
  }

  @Test
  void completionAndFailureReleaseTheTemporaryImageAndMakeRoomForTheNextJob() {
    when(supervisionConfig.recognitionConcurrency()).thenReturn(1);
    RecognitionJobView first = service.submit("keeper-a", imageRequest("reply-1"));
    RecognitionJobView second = service.submit("keeper-a", imageRequest("reply-2"));

    service.failForWorker(first.jobId(), "30-10001");

    assertThat(service.getOwned(first.jobId(), "keeper-a").status())
        .isEqualTo(RecognitionJobStatus.FAILED);
    assertThat(service.getOwned(second.jobId(), "keeper-a").status())
        .isEqualTo(RecognitionJobStatus.RECOGNIZING);
    verify(imageStore).delete("image-1");
  }

  @Test
  void workerReadsTheTemporaryImageBeforeProducingItsResult() {
    RecognitionJobView job = service.submit("keeper-a", imageRequest("reply-1"));

    executor.runNext();

    verify(imageStore).read("image-1");
    assertThat(service.getOwned(job.jobId(), "keeper-a").status())
        .isEqualTo(RecognitionJobStatus.READY);
    verify(imageStore).delete("image-1");
  }

  @Test
  void workerMarksRecognitionFailuresAndDeletesTheTemporaryImage() {
    when(orchestrationService.recognizeForJob(any(), any(), any(), any()))
        .thenThrow(new ApiException("30-10001", "recognition failed"));
    RecognitionJobView job = service.submit("keeper-a", imageRequest("reply-1"));

    executor.runNext();

    RecognitionJobView result = service.getOwned(job.jobId(), "keeper-a");
    assertThat(result.status()).isEqualTo(RecognitionJobStatus.FAILED);
    assertThat(result.errorCode()).isEqualTo("30-10001");
    verify(imageStore).delete("image-1");
  }

  @Test
  void workerDeletesTheTemporaryImageAfterARunningJobIsCancelled() {
    RecognitionJobView job = service.submit("keeper-a", imageRequest("reply-1"));
    service.cancelOwned(job.jobId(), "keeper-a");

    executor.runNext();

    assertThat(service.getOwned(job.jobId(), "keeper-a").status())
        .isEqualTo(RecognitionJobStatus.CANCELLED);
    verify(imageStore, never()).read("image-1");
    verify(orchestrationService, never()).recognizeForJob(any(), any(), any(), any());
    verify(imageStore).delete("image-1");
  }

  @Test
  void workerUsesTheEmployeeIdentityCapturedWhenTheJobWasSubmitted() {
    AuthUser owner = new AuthUser("keeper-a", "Owner", Role.KEEPER, null);
    AuthUser unrelatedWorker = new AuthUser("keeper-b", "Other", Role.KEEPER, null);
    RecognitionJobView job = service.submit(owner, imageRequest("reply-1"));
    AuthContext.set(unrelatedWorker);
    try {
      executor.runNext();

      org.mockito.ArgumentCaptor<AuthUser> employee = org.mockito.ArgumentCaptor.forClass(AuthUser.class);
      verify(orchestrationService).recognizeForJob(any(), any(), employee.capture(), any());
      assertThat(employee.getValue()).isEqualTo(owner);
      assertThat(AuthContext.current()).isEqualTo(unrelatedWorker);
      assertThat(service.getOwned(job.jobId(), "keeper-a").status())
          .isEqualTo(RecognitionJobStatus.READY);
    } finally {
      AuthContext.clear();
    }
  }

  @Test
  void expiringARunningJobReleasesItsSlotAndStartsTheNextQueuedJob() {
    when(supervisionConfig.recognitionConcurrency()).thenReturn(1);
    RecognitionJobView running = service.submit("keeper-a", imageRequest("reply-1"));
    RecognitionJobView queued = service.submit("keeper-a", imageRequest("reply-2"));
    when(imageStore.exists("image-1")).thenReturn(false);
    when(imageStore.exists("image-2")).thenReturn(true);

    service.expireJobsMissingTheirTemporaryImage();

    assertThat(service.getOwned(running.jobId(), "keeper-a").status())
        .isEqualTo(RecognitionJobStatus.EXPIRED);
    assertThat(service.getOwned(queued.jobId(), "keeper-a").status())
        .isEqualTo(RecognitionJobStatus.RECOGNIZING);
    assertThat(executor.submissions()).hasSize(2);
    verify(imageStore).delete("image-1");
  }

  @Test
  void expiresAReadyResultAfterTheTaskRetentionWindowSoItNoLongerConsumesCapacity() {
    when(supervisionConfig.unfinishedTaskCap()).thenReturn(1);
    MutableClock mutableClock = new MutableClock(Instant.parse("2026-07-24T10:00:00Z"));
    service = new RecognitionJobService(
        new RecognitionJobRepository(),
        imageStore,
        imageValidator,
        imagePreprocessor,
        supervisionConfig,
        executor,
        orchestrationService,
        taskConfig,
        null,
        mutableClock);
    RecognitionJobView ready = service.submit("keeper-a", imageRequest("reply-1"));
    service.completeForWorker(ready.jobId(), response(), RecognitionJobStatus.READY);
    mutableClock.advance(Duration.ofHours(24));

    service.expireJobsMissingTheirTemporaryImage();

    assertThat(service.getOwned(ready.jobId(), "keeper-a").status())
        .isEqualTo(RecognitionJobStatus.EXPIRED);
    assertThat(service.submit("keeper-a", imageRequest("reply-2")).status())
        .isEqualTo(RecognitionJobStatus.RECOGNIZING);
  }

  @Test
  void releasingAnExpiredReadyResultAllowsTheTwentyFirstTaskAfterTwentyUnfinishedTasks() {
    MutableClock mutableClock = new MutableClock(Instant.parse("2026-07-24T10:00:00Z"));
    service = new RecognitionJobService(
        new RecognitionJobRepository(),
        imageStore,
        imageValidator,
        imagePreprocessor,
        supervisionConfig,
        executor,
        orchestrationService,
        taskConfig,
        null,
        mutableClock);
    List<RecognitionJobView> jobs = new ArrayList<>();
    for (int index = 0; index < 20; index++) {
      jobs.add(service.submit("keeper-a", imageRequest("reply-" + index)));
    }
    assertThatThrownBy(() -> service.submit("keeper-a", imageRequest("reply-20")))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
            .isEqualTo(ApiErrorCodes.CONFLICT));
    service.completeForWorker(jobs.get(0).jobId(), response(), RecognitionJobStatus.READY);
    assertThatThrownBy(() -> service.submit("keeper-a", imageRequest("reply-20")))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
            .isEqualTo(ApiErrorCodes.CONFLICT));
    when(imageStore.exists(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
    mutableClock.advance(Duration.ofHours(24));

    service.expireJobsMissingTheirTemporaryImage();

    assertThat(service.submit("keeper-a", imageRequest("reply-20")).status())
        .isEqualTo(RecognitionJobStatus.QUEUED);
  }

  @Test
  void terminalHistoryPruningUsesASnapshotBeforeRemovingJobsForMultipleEmployees() {
    RecognitionJobRepository repository = new RecognitionJobRepository();
    RecognitionJob firstOwnerOld = terminalJob("job-a-1", "keeper-a", 1);
    RecognitionJob firstOwnerNew = terminalJob("job-a-2", "keeper-a", 2);
    RecognitionJob secondOwner = terminalJob("job-b-1", "keeper-b", 3);
    repository.save(firstOwnerOld);
    repository.save(firstOwnerNew);
    repository.save(secondOwner);

    assertThatCode(() -> repository.pruneTerminalHistory(1)).doesNotThrowAnyException();
    assertThat(repository.find("job-a-1")).isEmpty();
    assertThat(repository.find("job-a-2")).isPresent();
    assertThat(repository.find("job-b-1")).isPresent();
  }

  @Test
  void recentTaskCapKeepsUnfinishedWorkAndEvictsTheOldestTerminalRecordFirst() {
    RecognitionJobRepository repository = new RecognitionJobRepository();
    Instant createdAt = Instant.parse("2026-07-24T10:00:00Z");
    RecognitionJob queued = new RecognitionJob(
        "queued",
        "keeper-a",
        new AuthUser("keeper-a", "keeper-a", Role.KEEPER, null),
        "image-queued",
        imageRequest("reply-queued"),
        createdAt);
    RecognitionJob terminalOld = terminalJob("terminal-old", "keeper-a", 1);
    RecognitionJob terminalNew = terminalJob("terminal-new", "keeper-a", 2);
    repository.save(queued);
    repository.save(terminalOld);
    repository.save(terminalNew);

    repository.pruneTerminalHistory(2);

    assertThat(repository.find("queued")).isPresent();
    assertThat(repository.find("terminal-old")).isEmpty();
    assertThat(repository.find("terminal-new")).isPresent();
  }

  @Test
  void expiresAJobWhoseTemporaryImageWasRemovedByTheTtlSweep() {
    RecognitionJobView job = service.submit("keeper-a", imageRequest("reply-1"));
    when(imageStore.exists("image-1")).thenReturn(false);

    service.expireJobsMissingTheirTemporaryImage();

    assertThat(service.getOwned(job.jobId(), "keeper-a").status())
        .isEqualTo(RecognitionJobStatus.EXPIRED);
  }

  @Test
  void rejectsTextOnlyRequestsBecauseRecognitionJobsRequireAScreenshot() {
    ChatRecognizeRequest textOnly = new ChatRecognizeRequest(
        null, "customer chat", "Alice", "lead", "crm", List.of(), "reply-1");

    assertThatThrownBy(() -> service.submit("keeper-a", textOnly))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
            .isEqualTo(ApiErrorCodes.BAD_REQUEST));
  }

  @Test
  void validatesTheOriginalImageThenStoresOnlyThePreprocessedJpeg() {
    byte[] originalPng = "raw PNG screenshot".getBytes(StandardCharsets.UTF_8);
    byte[] preprocessedJpeg = "preprocessed JPEG".getBytes(StandardCharsets.UTF_8);
    when(imagePreprocessor.preprocess(originalPng)).thenReturn(preprocessedJpeg);

    service.submit("keeper-a", new ChatRecognizeRequest(
        Base64.getEncoder().encodeToString(originalPng),
        null,
        "Alice",
        "lead",
        "crm",
        List.of(),
        "reply-1"));

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(
        imageValidator,
        imagePreprocessor,
        imageStore);
    order.verify(imageValidator).validate(originalPng);
    order.verify(imagePreprocessor).preprocess(originalPng);
    order.verify(imageStore).put(preprocessedJpeg);
  }

  private ChatRecognizeRequest imageRequest(String sessionId) {
    return new ChatRecognizeRequest(
        Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8)),
        null,
        "Alice",
        "lead",
        "crm",
        List.of(),
        sessionId);
  }

  private ChatResponse response() {
    return new ChatResponse(null, "Alice", false, null, null, null, null);
  }

  private RecognitionJob terminalJob(String jobId, String username, long second) {
    Instant createdAt = Instant.parse("2026-07-24T10:00:00Z").plusSeconds(second);
    RecognitionJob job = new RecognitionJob(
        jobId,
        username,
        new AuthUser(username, username, Role.KEEPER, null),
        "image-" + jobId,
        imageRequest("reply-" + jobId),
        createdAt);
    job.start(createdAt);
    job.fail("30-10001", createdAt);
    return job;
  }

  private static final class RecordingExecutor implements Executor {
    private final List<Runnable> submissions = new ArrayList<>();
    private int imageToken;

    @Override
    public void execute(Runnable command) {
      submissions.add(command);
    }

    List<Runnable> submissions() {
      return submissions;
    }

    int nextImageToken() {
      imageToken++;
      return imageToken;
    }

    void runNext() {
      submissions.remove(0).run();
    }
  }

  private static final class MutableClock extends Clock {
    private Instant current;

    private MutableClock(Instant current) {
      this.current = current;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return current;
    }

    void advance(Duration duration) {
      current = current.plus(duration);
    }
  }
}
