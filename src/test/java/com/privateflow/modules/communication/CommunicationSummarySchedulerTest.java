package com.privateflow.modules.communication;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CommunicationSummarySchedulerTest {

  @Test
  void processesEveryDueCustomerOutsideTheRecognitionFlow() {
    CommunicationArchiveRepository repository = Mockito.mock(CommunicationArchiveRepository.class);
    CommunicationSummaryProcessor processor = Mockito.mock(CommunicationSummaryProcessor.class);
    Clock clock = Clock.fixed(
        Instant.parse("2026-08-01T02:20:00Z"),
        ZoneId.of("Asia/Shanghai"));
    LocalDateTime now = LocalDateTime.parse("2026-08-01T10:20:00");
    when(repository.findSummaryStatesDue(now, 100)).thenReturn(List.of(
        new CommunicationSummaryState(7L, "PENDING", null, 0, null, null, now),
        new CommunicationSummaryState(8L, "RETRY_PENDING", 20L, 1, now, "timeout", now)));
    CommunicationSummaryScheduler scheduler =
        new CommunicationSummaryScheduler(repository, processor, clock);

    scheduler.processDueSummaries();

    verify(processor).process(7L);
    verify(processor).process(8L);
  }
}
