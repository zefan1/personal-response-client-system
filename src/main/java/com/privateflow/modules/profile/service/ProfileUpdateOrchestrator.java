package com.privateflow.modules.profile.service;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.profile.ProfileUpdateException;
import com.privateflow.modules.profile.config.ProfileConfigProvider;
import com.privateflow.modules.profile.infra.AuditLogRepository;
import com.privateflow.modules.profile.infra.ProfileWriter;
import com.privateflow.modules.skill.FieldUpdate;
import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.tags.AutomaticCustomerTagUpdateRequest;
import com.privateflow.modules.tags.CustomerTagUpdateService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ProfileUpdateOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(ProfileUpdateOrchestrator.class);
  private final EventDeduplicator deduplicator;
  private final CustomerQueryService customerQueryService;
  private final ProfileExtractionClient extractionClient;
  private final ConfidenceRouter confidenceRouter;
  private final ProfileWriter profileWriter;
  private final SuggestionQueueManager suggestionQueueManager;
  private final CustomerTagUpdateService customerTagUpdateService;
  private final ProfileConfigProvider configProvider;
  private final AuditLogRepository auditLogRepository;

  public ProfileUpdateOrchestrator(
      EventDeduplicator deduplicator,
      CustomerQueryService customerQueryService,
      ProfileExtractionClient extractionClient,
      ConfidenceRouter confidenceRouter,
      ProfileWriter profileWriter,
      SuggestionQueueManager suggestionQueueManager,
      CustomerTagUpdateService customerTagUpdateService,
      ProfileConfigProvider configProvider,
      AuditLogRepository auditLogRepository) {
    this.deduplicator = deduplicator;
    this.customerQueryService = customerQueryService;
    this.extractionClient = extractionClient;
    this.confidenceRouter = confidenceRouter;
    this.profileWriter = profileWriter;
    this.suggestionQueueManager = suggestionQueueManager;
    this.customerTagUpdateService = customerTagUpdateService;
    this.configProvider = configProvider;
    this.auditLogRepository = auditLogRepository;
  }

  @Async("profileUpdateExecutor")
  @EventListener
  public void handleEvent(CustomerMessageSentEvent event) {
    if (event == null || event.phone() == null || event.phone().isBlank()) {
      return;
    }
    try {
      String conversation = conversationText(event);
      if (deduplicator.seenRecently(event.phone(), conversation)) {
        log.debug("skip duplicated profile update event, phone={}", event.phone());
        return;
      }
      Customer customer = customerQueryService.getByPhone(event.phone());
      if (customer == null) {
        log.info("customer missing, skip profile update, phone={}", event.phone());
        return;
      }
      ProfileAnalysisResult analysis = extractionClient.extract(
          conversation,
          event.rawMessages(),
          customer,
          event.operator());
      RoutedProfileUpdates routed = confidenceRouter.route(analysis.profileUpdates());
      Map<String, Object> autoWrite = new LinkedHashMap<>();
      routed.high().forEach((field, update) -> autoWrite.put(field, update.value()));
      autoWrite.put("lastFollowupAt", java.time.LocalDateTime.now());
      autoWrite.put("followupNotes", fallbackSummary(event));
      Integer writtenVersion = null;
      try {
        writtenVersion = profileWriter.write(event.phone(), autoWrite, customer.getVersion(), true);
      } catch (ProfileUpdateException ex) {
        log.warn("profile auto update skipped by conflict, phone={}", event.phone());
      }
      if (writtenVersion != null
          && customer.getId() != null
          && !analysis.tagDecisions().isEmpty()) {
        try {
          customerTagUpdateService.applyAutomatic(new AutomaticCustomerTagUpdateRequest(
              customer.getId(),
              event.phone(),
              writtenVersion,
              effectiveCustomerMessageCount(event),
              event.operator(),
              analysis.tagDecisions()));
        } catch (RuntimeException ex) {
          log.warn(
              "automatic customer tag update failed, normal profile flow continues, phone={}, reason={}",
              event.phone(),
              ex.getMessage());
        }
      }
      suggestionQueueManager.enqueue(event.phone(), customer, routed.medium());
      auditLogRepository.log("UPDATE_PROFILE", "SYSTEM", "customer", event.phone(), "auto profile update");
    } catch (RuntimeException ex) {
      log.error("profile update event failed, phone={}", event.phone(), ex);
    }
  }

  private String conversationText(CustomerMessageSentEvent event) {
    if (event.conversationSummary() != null && !event.conversationSummary().isBlank()) {
      return event.conversationSummary();
    }
    return fallbackSummary(event);
  }

  private String fallbackSummary(CustomerMessageSentEvent event) {
    StringBuilder builder = new StringBuilder();
    if (event.rawMessages() != null) {
      for (CustomerMessageSentEvent.ChatMessage message : event.rawMessages()) {
        if (message != null && "client".equalsIgnoreCase(message.role()) && message.text() != null) {
          builder.append(message.text()).append('\n');
        }
      }
    }
    String text = builder.toString().trim();
    int limit = configProvider.get().fallbackSummaryChars();
    return text.length() > limit ? text.substring(0, limit) : text;
  }

  private int effectiveCustomerMessageCount(CustomerMessageSentEvent event) {
    if (event.rawMessages() == null) {
      return 0;
    }
    return (int) event.rawMessages().stream()
        .filter(message -> message != null
            && message.text() != null
            && !message.text().isBlank()
            && ("client".equalsIgnoreCase(message.role())
                || "customer".equalsIgnoreCase(message.role())))
        .count();
  }
}
