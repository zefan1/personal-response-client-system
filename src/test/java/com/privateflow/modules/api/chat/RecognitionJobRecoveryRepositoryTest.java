package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class RecognitionJobRecoveryRepositoryTest {

  private RecognitionJobRecoveryRepository repository;

  @BeforeEach
  void setUp() {
    DataSource dataSource = new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .setName("recognition-recovery-" + UUID.randomUUID())
        .build();
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("""
        CREATE TABLE recognition_job_restart_recovery (
          job_id VARCHAR(64) PRIMARY KEY,
          username VARCHAR(64) NOT NULL,
          reply_session_id VARCHAR(128),
          status VARCHAR(32) NOT NULL,
          error_code VARCHAR(64),
          created_at TIMESTAMP NOT NULL,
          updated_at TIMESTAMP NOT NULL
        )
        """);
    repository = new RecognitionJobRecoveryRepository(jdbcTemplate);
  }

  @Test
  void restartMarksUndeliveredTasksAsFailedWithoutPersistingAnyRecognitionPayload() {
    Instant createdAt = Instant.parse("2026-08-17T04:00:00Z");
    repository.save(view("queued", RecognitionJobStatus.QUEUED, null, createdAt), "keeper-a");
    repository.save(view("ready", RecognitionJobStatus.READY, null, createdAt), "keeper-a");
    repository.save(view("failed", RecognitionJobStatus.FAILED, "30-10001", createdAt), "keeper-a");

    int recovered = repository.markRestartedTasksFailed(createdAt.plusSeconds(30));

    assertThat(recovered).isEqualTo(2);
    assertThat(repository.find("queued").orElseThrow().view())
        .extracting(RecognitionJobView::status, RecognitionJobView::errorCode, RecognitionJobView::response)
        .containsExactly(RecognitionJobStatus.FAILED, RecognitionJobRecoveryRepository.BACKEND_RESTARTED, null);
    assertThat(repository.find("ready").orElseThrow().view().errorCode())
        .isEqualTo(RecognitionJobRecoveryRepository.BACKEND_RESTARTED);
    assertThat(repository.find("failed").orElseThrow().view().errorCode()).isEqualTo("30-10001");
  }

  @Test
  void recoveredTaskKeepsOwnershipAndSafePollingMetadataOnly() {
    Instant createdAt = Instant.parse("2026-08-17T04:00:00Z");
    repository.save(view("job-1", RecognitionJobStatus.RECOGNIZING, null, createdAt), "keeper-a");
    repository.markRestartedTasksFailed(createdAt.plusSeconds(1));

    RecognitionJobRecoveryRepository.RecoveredRecognitionJob recovered = repository.find("job-1").orElseThrow();

    assertThat(recovered.username()).isEqualTo("keeper-a");
    assertThat(recovered.view())
        .extracting(RecognitionJobView::replySessionId, RecognitionJobView::response)
        .containsExactly("reply-job-1", null);
  }

  private RecognitionJobView view(
      String jobId, RecognitionJobStatus status, String errorCode, Instant createdAt) {
    return new RecognitionJobView(
        jobId, "reply-" + jobId, status, errorCode, null, createdAt, createdAt);
  }
}
