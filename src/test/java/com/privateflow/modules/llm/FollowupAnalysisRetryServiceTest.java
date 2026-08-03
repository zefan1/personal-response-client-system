package com.privateflow.modules.llm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.CustomerFollowupAnalysisCompletedEvent;
import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.profile.service.FollowupAnalysisFieldMerger;
import com.privateflow.modules.profile.service.FollowupConfirmationService;
import com.privateflow.modules.tablewrite.config.TableConfig;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class FollowupAnalysisRetryServiceTest {

  @Test
  void successfulRetryUpdatesTheLocalProfileAndPublishesTheSameFieldsForTableWrite() throws Exception {
    PendingFollowupAnalysisRepository repository = mock(PendingFollowupAnalysisRepository.class);
    LlmFollowupAnalysisService analysisService = mock(LlmFollowupAnalysisService.class);
    CustomerQueryService customerQueryService = mock(CustomerQueryService.class);
    FollowupAnalysisFieldMerger merger = mock(FollowupAnalysisFieldMerger.class);
    FollowupConfirmationService confirmationService = mock(FollowupConfirmationService.class);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    TableConfigProvider configProvider = config();
    ObjectMapper objectMapper = new ObjectMapper();
    FollowupAnalysisRetryPayload retryPayload = payload();
    PendingFollowupAnalysis pending = pending(objectMapper.writeValueAsString(retryPayload));
    Customer customer = new Customer();
    customer.setPhone("18800001111");
    FollowupAnalysisPayload analysis = new FollowupAnalysisPayload(
        "内部提醒", "腹直肌分离", "客户B档案", "本次跟进记录", "高意向",
        "确认到店", "2026-08-03T10:00", "周一可联系");
    Map<String, Object> fields = Map.of(
        "internalNote", "内部提醒",
        "followupNotes", "本次跟进记录",
        "nextFollowupDir", "确认到店");
    when(repository.due(100)).thenReturn(List.of(pending));
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    when(analysisService.tryAnalyze(any())).thenReturn(Optional.of(analysis));
    when(merger.merge(customer, analysis)).thenReturn(fields);
    FollowupAnalysisRetryService service = new FollowupAnalysisRetryService(
        repository, analysisService, customerQueryService, merger, confirmationService,
        publisher, configProvider, objectMapper);

    service.retryDueAnalyses();

    verify(confirmationService).recordAnalysis(customer, fields, false);
    verify(publisher).publishEvent(new CustomerFollowupAnalysisCompletedEvent("18800001111", fields));
    verify(repository).markResolved(7L);
    verify(repository, never()).markRetry(any(Long.class), any(Integer.class), any(), any());
  }

  @Test
  void unavailableLlmKeepsTheQueuePendingForAnotherRetry() throws Exception {
    PendingFollowupAnalysisRepository repository = mock(PendingFollowupAnalysisRepository.class);
    LlmFollowupAnalysisService analysisService = mock(LlmFollowupAnalysisService.class);
    CustomerQueryService customerQueryService = mock(CustomerQueryService.class);
    ObjectMapper objectMapper = new ObjectMapper();
    PendingFollowupAnalysis pending = pending(objectMapper.writeValueAsString(payload()));
    Customer customer = new Customer();
    customer.setPhone("18800001111");
    when(repository.due(100)).thenReturn(List.of(pending));
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    when(analysisService.tryAnalyze(any())).thenReturn(Optional.empty());
    FollowupAnalysisRetryService service = new FollowupAnalysisRetryService(
        repository,
        analysisService,
        customerQueryService,
        mock(FollowupAnalysisFieldMerger.class),
        mock(FollowupConfirmationService.class),
        mock(ApplicationEventPublisher.class),
        config(),
        objectMapper);

    service.retryDueAnalyses();

    verify(repository).markRetry(eq(7L), eq(1), any(LocalDateTime.class), contains("LLM"));
    verify(repository, never()).markResolved(7L);
  }

  private TableConfigProvider config() {
    TableConfigProvider provider = mock(TableConfigProvider.class);
    when(provider.get()).thenReturn(new TableConfig("", "", 5000, 3, 30, 1, "ADMIN", 50, 500));
    return provider;
  }

  private PendingFollowupAnalysis pending(String payload) {
    PendingFollowupAnalysis pending = new PendingFollowupAnalysis();
    pending.setId(7L);
    pending.setPhone("18800001111");
    pending.setPayload(payload);
    pending.setRetryCount(0);
    return pending;
  }

  private FollowupAnalysisRetryPayload payload() {
    return new FollowupAnalysisRetryPayload(
        "18800001111",
        List.of(new CustomerMessageSentEvent.ChatMessage("client", "周一上午联系", "12:00")),
        "好的，周一联系您",
        "NEXT_STEP",
        "keeper");
  }
}
