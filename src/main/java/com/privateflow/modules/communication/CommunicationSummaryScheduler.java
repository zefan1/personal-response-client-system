package com.privateflow.modules.communication;

import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CommunicationSummaryScheduler {

  private static final Logger log = LoggerFactory.getLogger(CommunicationSummaryScheduler.class);
  private static final int BATCH_SIZE = 100;
  private final CommunicationArchiveRepository repository;
  private final CommunicationSummaryProcessor processor;
  private final Clock clock;

  @Autowired
  public CommunicationSummaryScheduler(
      CommunicationArchiveRepository repository,
      CommunicationSummaryProcessor processor) {
    this(repository, processor, Clock.systemDefaultZone());
  }

  CommunicationSummaryScheduler(
      CommunicationArchiveRepository repository,
      CommunicationSummaryProcessor processor,
      Clock clock) {
    this.repository = repository;
    this.processor = processor;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${communication.summary.scan-interval-ms:30000}")
  public void processDueSummaries() {
    for (CommunicationSummaryState state
        : repository.findSummaryStatesDue(LocalDateTime.now(clock), BATCH_SIZE)) {
      try {
        processor.process(state.customerId());
      } catch (RuntimeException ex) {
        log.warn(
            "communication summary update failed, customerId={}, reason={}",
            state.customerId(),
            ex.getMessage());
      }
    }
  }
}
