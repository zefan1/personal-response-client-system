package com.privateflow.modules.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.llm.LlmSummaryInput;
import com.privateflow.modules.llm.LlmSummaryService;
import com.privateflow.modules.profile.infra.ProfileWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class CommunicationSummaryProcessorTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private static final LocalDateTime NOW = LocalDateTime.parse("2026-08-01T10:20:00");
  private CommunicationArchiveRepository repository;
  private LlmSummaryService summaryService;
  private ProfileWriter profileWriter;
  private CommunicationSummaryProcessor processor;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(CommunicationArchiveRepository.class);
    summaryService = Mockito.mock(LlmSummaryService.class);
    profileWriter = Mockito.mock(ProfileWriter.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-01T02:20:00Z"), ZONE);
    processor = new CommunicationSummaryProcessor(
        repository,
        summaryService,
        profileWriter,
        clock,
        Duration.ofMinutes(5));
  }

  @Test
  void successfulUpdateUsesOnlyNewMessagesAndKeepsAHistoryVersion() {
    when(repository.findSummaryState(7L)).thenReturn(Optional.of(
        new CommunicationSummaryState(7L, "PENDING", 10L, 0, null, null, NOW.minusMinutes(1))));
    when(repository.findMessagesAfter(7L, 10L, 100)).thenReturn(List.of(
        message(11L, "CUSTOMER", "最近一直腰痛"),
        message(12L, "EMPLOYEE", "可以先安排基础评估")));
    when(repository.findSummaryVersions(7L)).thenReturn(List.of(
        new CommunicationSummaryVersion(1L, 7L, 1, "旧汇总", 10L, NOW.minusDays(1))));
    when(repository.findCustomerPhone(7L)).thenReturn(Optional.of("18800001111"));
    when(summaryService.trySummarize(any())).thenReturn(Optional.of("新汇总"));

    processor.process(7L);

    ArgumentCaptor<LlmSummaryInput> input = ArgumentCaptor.forClass(LlmSummaryInput.class);
    verify(summaryService).trySummarize(input.capture());
    assertThat(input.getValue().sentText()).isEqualTo("上一版沟通汇总：旧汇总");
    assertThat(input.getValue().rawMessages())
        .extracting(item -> item.role() + ":" + item.text())
        .containsExactly("CUSTOMER:最近一直腰痛", "EMPLOYEE:可以先安排基础评估");
    verify(repository).appendSummaryVersion(7L, "新汇总", 12L, NOW);
    verify(profileWriter).write(
        "18800001111",
        Map.of("customerProfileSummary", "新汇总"),
        null,
        true);
  }

  @Test
  void failedUpdateKeepsTheExistingSummaryAndSchedulesARetry() {
    when(repository.findSummaryState(7L)).thenReturn(Optional.of(
        new CommunicationSummaryState(7L, "RETRY_PENDING", 10L, 2, NOW, "timeout", NOW)));
    when(repository.findMessagesAfter(7L, 10L, 100)).thenReturn(List.of(
        message(11L, "CUSTOMER", "我想先了解怎么做")));
    when(repository.findSummaryVersions(7L)).thenReturn(List.of(
        new CommunicationSummaryVersion(1L, 7L, 1, "旧汇总", 10L, NOW.minusDays(1))));
    when(repository.findCustomerPhone(7L)).thenReturn(Optional.of("18800001111"));
    when(summaryService.trySummarize(any())).thenReturn(Optional.empty());

    processor.process(7L);

    verify(repository).markSummaryFailed(
        eq(7L),
        eq(3),
        eq(NOW.plusMinutes(5)),
        any());
    verify(repository, never()).appendSummaryVersion(any(Long.class), any(), any(Long.class), any());
    verify(profileWriter, never()).write(any(), any(), any(), eq(true));
  }

  private ArchivedCommunicationMessage message(long id, String role, String text) {
    return new ArchivedCommunicationMessage(
        id, 1L, 7L, "keeper-1", "WECHAT", role, "TEXT", text, text,
        NOW.minusMinutes(1), false, (int) id, "fingerprint-" + id);
  }
}
