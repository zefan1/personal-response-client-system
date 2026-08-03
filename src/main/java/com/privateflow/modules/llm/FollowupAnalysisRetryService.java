package com.privateflow.modules.llm;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FollowupAnalysisRetryService {

  private static final Logger log = LoggerFactory.getLogger(FollowupAnalysisRetryService.class);
  private final PendingFollowupAnalysisRepository repository;
  private final LlmFollowupAnalysisService analysisService;
  private final CustomerQueryService customerQueryService;
  private final FollowupAnalysisFieldMerger merger;
  private final FollowupConfirmationService confirmationService;
  private final ApplicationEventPublisher eventPublisher;
  private final TableConfigProvider configProvider;
  private final ObjectMapper objectMapper;

  public FollowupAnalysisRetryService(
      PendingFollowupAnalysisRepository repository,
      LlmFollowupAnalysisService analysisService,
      CustomerQueryService customerQueryService,
      FollowupAnalysisFieldMerger merger,
      FollowupConfirmationService confirmationService,
      ApplicationEventPublisher eventPublisher,
      TableConfigProvider configProvider,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.analysisService = analysisService;
    this.customerQueryService = customerQueryService;
    this.merger = merger;
    this.confirmationService = confirmationService;
    this.eventPublisher = eventPublisher;
    this.configProvider = configProvider;
    this.objectMapper = objectMapper;
  }

  public void enqueue(
      String confirmationId,
      String phone,
      List<CustomerMessageSentEvent.ChatMessage> rawMessages,
      String sentText,
      String selectedDirection,
      String operator) {
    try {
      FollowupAnalysisRetryPayload payload = new FollowupAnalysisRetryPayload(
          phone,
          rawMessages == null ? List.of() : rawMessages,
          sentText,
          selectedDirection,
          operator);
      String requestKey = requestKey(confirmationId, payload);
      repository.enqueue(
          requestKey,
          phone,
          objectMapper.writeValueAsString(payload),
          LocalDateTime.now().plusSeconds(configProvider.get().retryIntervalS()),
          "LLM followup analysis unavailable");
    } catch (RuntimeException ex) {
      log.error("failed to enqueue followup analysis retry, phoneLast4={}, reason={}", last4(phone), ex.getMessage());
    } catch (Exception ex) {
      log.error("failed to serialize followup analysis retry, phoneLast4={}, reason={}", last4(phone), ex.getMessage());
    }
  }

  @Scheduled(fixedDelayString = "#{@tableConfigProvider.get().retryIntervalS() * 1000L}")
  public void retryDueAnalyses() {
    TableConfig config = configProvider.get();
    for (PendingFollowupAnalysis item : repository.due(100)) {
      try {
        FollowupAnalysisRetryPayload payload = objectMapper.readValue(
            item.getPayload(), FollowupAnalysisRetryPayload.class);
        Customer customer = customerQueryService.getByPhone(payload.phone());
        if (customer == null) {
          throw new IllegalStateException("customer is not available for LLM analysis retry");
        }
        FollowupAnalysisPayload analysis = analysisService.tryAnalyze(new LlmFollowupAnalysisInput(
            customer,
            payload.rawMessages(),
            payload.sentText(),
            payload.selectedDirection(),
            payload.operator())).orElseThrow(
                () -> new IllegalStateException("LLM followup analysis is still unavailable"));
        Map<String, Object> fields = merger.merge(customer, analysis);
        confirmationService.recordAnalysis(customer, fields, false);
        eventPublisher.publishEvent(new CustomerFollowupAnalysisCompletedEvent(payload.phone(), fields));
        repository.markResolved(item.getId());
      } catch (Exception ex) {
        int nextRetry = item.getRetryCount() + 1;
        if (nextRetry >= config.retryMaxCount()) {
          repository.markFailed(item.getId(), nextRetry, ex.getMessage());
        } else {
          repository.markRetry(
              item.getId(),
              nextRetry,
              LocalDateTime.now().plusSeconds(config.retryIntervalS()),
              ex.getMessage());
        }
      }
    }
  }

  private String requestKey(String confirmationId, FollowupAnalysisRetryPayload payload) {
    if (confirmationId != null && !confirmationId.isBlank()) {
      return "confirmation:" + confirmationId.trim();
    }
    return "payload:" + payload.phone() + ":" + Integer.toUnsignedString(payload.hashCode(), 36);
  }

  private String last4(String phone) {
    if (phone == null) {
      return "";
    }
    return phone.substring(Math.max(0, phone.length() - 4));
  }
}
