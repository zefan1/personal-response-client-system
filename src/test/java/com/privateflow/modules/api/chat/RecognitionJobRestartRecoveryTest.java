package com.privateflow.modules.api.chat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RecognitionJobRestartRecoveryTest {

  @Test
  void marksPersistedUndeliveredJobsAsRestartFailuresWhenTheApplicationStarts() throws Exception {
    RecognitionJobRecoveryRepository repository = mock(RecognitionJobRecoveryRepository.class);
    Instant startup = Instant.parse("2026-08-17T05:00:00Z");
    RecognitionJobRestartRecovery recovery = new RecognitionJobRestartRecovery(
        repository, Clock.fixed(startup, ZoneOffset.UTC));

    recovery.run(null);

    verify(repository).markRestartedTasksFailed(startup);
  }
}
