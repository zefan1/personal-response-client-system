package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.image.processing.ImagePreprocessor;
import com.privateflow.modules.image.processing.ImageValidator;
import com.privateflow.modules.supervision.SupervisionConfig;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecognitionJobServiceTest {

  private final RecordingExecutor executor = new RecordingExecutor();
  private final TemporaryRecognitionImageStore imageStore = mock(TemporaryRecognitionImageStore.class);
  private final ImageValidator imageValidator = mock(ImageValidator.class);
  private final ImagePreprocessor imagePreprocessor = mock(ImagePreprocessor.class);
  private final SupervisionConfig supervisionConfig = mock(SupervisionConfig.class);
  private RecognitionJobService service;

  @BeforeEach
  void setUp() {
    when(supervisionConfig.recognitionConcurrency()).thenReturn(4);
    when(supervisionConfig.unfinishedTaskCap()).thenReturn(20);
    when(imagePreprocessor.preprocess(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(imageStore.put(any())).thenAnswer(invocation -> "image-" + executor.nextImageToken());
    service = new RecognitionJobService(
        new RecognitionJobRepository(),
        imageStore,
        imageValidator,
        imagePreprocessor,
        supervisionConfig,
        executor,
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
  }
}
