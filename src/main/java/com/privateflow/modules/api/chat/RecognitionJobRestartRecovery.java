package com.privateflow.modules.api.chat;

import java.time.Clock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Marks incomplete in-memory recognition work as failed after a backend restart instead of retrying it. */
@Component
public class RecognitionJobRestartRecovery implements ApplicationRunner {

  private final RecognitionJobRecoveryRepository repository;
  private final Clock clock;

  @Autowired
  public RecognitionJobRestartRecovery(RecognitionJobRecoveryRepository repository) {
    this(repository, Clock.systemUTC());
  }

  RecognitionJobRestartRecovery(RecognitionJobRecoveryRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    repository.markRestartedTasksFailed(clock.instant());
  }
}
