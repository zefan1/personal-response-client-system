package com.privateflow.modules.llm;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.common.events.FollowupWsMessageReadyEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class LlmAbnormalDetectionListener {

  private final LlmAbnormalDetectionService detectionService;
  private final CustomerQueryService customerQueryService;
  private final ApplicationEventPublisher eventPublisher;

  public LlmAbnormalDetectionListener(
      LlmAbnormalDetectionService detectionService,
      CustomerQueryService customerQueryService,
      ApplicationEventPublisher eventPublisher) {
    this.detectionService = detectionService;
    this.customerQueryService = customerQueryService;
    this.eventPublisher = eventPublisher;
  }

  @Async("profileUpdateExecutor")
  @EventListener
  public void onCustomerMessageSent(CustomerMessageSentEvent event) {
    if (event == null || event.phone() == null || event.phone().isBlank() || !detectionService.enabled()) {
      return;
    }
    Customer customer = customerQueryService.getByPhone(event.phone());
    String userId = customer == null ? event.operator() : firstNonBlank(customer.getAssignedKeeper(), event.operator());
    if (userId == null || userId.isBlank()) {
      return;
    }
    detectionService.tryDetect(new LlmAbnormalDetectionInput(
        event.phone(),
        firstNonBlank(event.nickname(), customer == null ? "" : customer.getNickname()),
        firstNonBlank(event.leadType(), customer == null ? "" : customer.getLeadType()),
        event.conversationSummary(),
        event.rawMessages(),
        event.sentText(),
        event.operator()))
        .ifPresent(alert -> eventPublisher.publishEvent(new FollowupWsMessageReadyEvent(
            userId,
            "ABNORMAL_ALERT",
            Map.of(
                "phone", event.phone(),
                "alertType", alert.alertType(),
                "message", alert.message(),
                "level", alert.level(),
                "occurredAt", LocalDateTime.now().toString()))));
  }

  private String firstNonBlank(String first, String second) {
    return first != null && !first.isBlank() ? first : second;
  }
}
