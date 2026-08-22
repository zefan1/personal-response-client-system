package com.privateflow.modules.profile.service;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.common.events.RecognizedConversationEvent;
import com.privateflow.common.events.RecognizedProfileFactsUpdatedEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.profile.ProfileUpdateException;
import com.privateflow.modules.customer.history.CustomerFieldHistoryContext;
import com.privateflow.modules.profile.config.ProfileConfigProvider;
import com.privateflow.modules.profile.infra.AuditLogRepository;
import com.privateflow.modules.profile.infra.ProfileWriter;
import com.privateflow.modules.profile.infra.ProfileUpdateFailureRepository;
import com.privateflow.modules.skill.FieldUpdate;
import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.tags.AutomaticCustomerTagUpdateRequest;
import com.privateflow.modules.tags.CustomerTagUpdateService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
  private final ApplicationEventPublisher eventPublisher;
  private final ProfileUpdateFailureRepository failureRepository;

  @Autowired
  public ProfileUpdateOrchestrator(
      EventDeduplicator deduplicator,
      CustomerQueryService customerQueryService,
      ProfileExtractionClient extractionClient,
      ConfidenceRouter confidenceRouter,
      ProfileWriter profileWriter,
      SuggestionQueueManager suggestionQueueManager,
      CustomerTagUpdateService customerTagUpdateService,
      ProfileConfigProvider configProvider,
      AuditLogRepository auditLogRepository,
      ApplicationEventPublisher eventPublisher,
      ProfileUpdateFailureRepository failureRepository) {
    this.deduplicator = deduplicator;
    this.customerQueryService = customerQueryService;
    this.extractionClient = extractionClient;
    this.confidenceRouter = confidenceRouter;
    this.profileWriter = profileWriter;
    this.suggestionQueueManager = suggestionQueueManager;
    this.customerTagUpdateService = customerTagUpdateService;
    this.configProvider = configProvider;
    this.auditLogRepository = auditLogRepository;
    this.eventPublisher = eventPublisher;
    this.failureRepository = failureRepository;
  }

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
    this(
        deduplicator,
        customerQueryService,
        extractionClient,
        confidenceRouter,
        profileWriter,
        suggestionQueueManager,
        customerTagUpdateService,
        configProvider,
        auditLogRepository,
        null,
        null);
  }

  public ProfileUpdateOrchestrator(
      EventDeduplicator deduplicator,
      CustomerQueryService customerQueryService,
      ProfileExtractionClient extractionClient,
      ConfidenceRouter confidenceRouter,
      ProfileWriter profileWriter,
      SuggestionQueueManager suggestionQueueManager,
      CustomerTagUpdateService customerTagUpdateService,
      ProfileConfigProvider configProvider,
      AuditLogRepository auditLogRepository,
      ApplicationEventPublisher eventPublisher) {
    this(
        deduplicator,
        customerQueryService,
        extractionClient,
        confidenceRouter,
        profileWriter,
        suggestionQueueManager,
        customerTagUpdateService,
        configProvider,
        auditLogRepository,
        eventPublisher,
        null);
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
      Integer writtenVersion = customer.getVersion();
      if (!autoWrite.isEmpty()) {
        try {
          writtenVersion = profileWriter.write(
              event.phone(),
              autoWrite,
              customer.getVersion(),
              true,
              CustomerFieldHistoryContext.of("会话识别", "客户对话文本", "SYSTEM"));
        } catch (ProfileUpdateException ex) {
          writtenVersion = null;
          log.warn("profile auto update skipped by conflict, phone={}", event.phone());
        }
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
    String customerEvidence = fallbackSummary(event);
    if (customerEvidence.isBlank()) {
      return "";
    }
    if (event.conversationSummary() != null && !event.conversationSummary().isBlank()) {
      return event.conversationSummary();
    }
    return customerEvidence;
  }

  @Async("profileUpdateExecutor")
  @EventListener
  public void handleRecognition(RecognizedConversationEvent event) {
    if (event == null || event.customerId() == null || event.customerId() <= 0) {
      return;
    }
    String stage = "CUSTOMER_LOAD";
    try {
      Customer customer = customerQueryService.getById(event.customerId());
      if (customer == null) {
        return;
      }
      String conversation = recognitionConversationText(event.rawMessages());
      if (conversation.isBlank()
          || deduplicator.seenRecently("recognition:" + event.customerId(), conversation)) {
        return;
      }
      stage = "PROFILE_EXTRACTION";
      ProfileAnalysisResult analysis = extractionClient.extract(
          conversation, event.rawMessages(), customer, event.operator());
      RoutedProfileUpdates routed = confidenceRouter.route(analysis.profileUpdates());
      Map<String, Object> immediateFacts = new LinkedHashMap<>();
      routed.high().forEach((field, update) -> {
        if (!deferredUntilSend(field)) {
          immediateFacts.put(field, update.value());
        }
      });
      if (!immediateFacts.isEmpty()) {
        stage = "PROFILE_WRITE";
        profileWriter.writeByCustomerId(
            customer.getId(),
            immediateFacts,
            customer.getVersion(),
            true,
            CustomerFieldHistoryContext.of("会话识别", "客户对话文本", "SYSTEM"));
        if (eventPublisher != null) {
          eventPublisher.publishEvent(new RecognizedProfileFactsUpdatedEvent(
              customer.getId(), new LinkedHashMap<>(immediateFacts)));
        }
      }
      if (customer.getPhone() != null && !customer.getPhone().isBlank()) {
        stage = "SUGGESTION_QUEUE";
        Map<String, FieldUpdate> immediateSuggestions = new LinkedHashMap<>();
        routed.medium().forEach((field, update) -> {
          if (!deferredUntilSend(field)) {
            immediateSuggestions.put(field, update);
          }
        });
        suggestionQueueManager.enqueue(customer.getPhone(), customer, immediateSuggestions);
      }
      auditLogRepository.log("UPDATE_PROFILE", "SYSTEM", "customer", String.valueOf(event.customerId()), "recognition fact extract");
      if (event.failureId() != null && failureRepository != null) {
        failureRepository.markSucceeded(event.failureId());
      }
    } catch (RuntimeException ex) {
      if (failureRepository != null) {
        if (event.failureId() != null) {
          failureRepository.markFailed(event.failureId(), stage, ex);
        } else {
          failureRepository.recordFailure(event.customerId(), event.phone(), event.rawMessages(), event.operator(), stage, ex);
        }
      }
      auditLogRepository.log("PROFILE_UPDATE_FAILED", "SYSTEM", "customer", String.valueOf(event.customerId()),
          "stage=" + stage + "; error=" + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
      log.warn("recognition profile fact extract failed, customerId={}", event.customerId(), ex);
    }
  }

  private String recognitionConversationText(
      java.util.List<CustomerMessageSentEvent.ChatMessage> messages) {
    if (messages == null) {
      return "";
    }
    return messages.stream()
        .filter(message -> message != null && message.text() != null)
        .filter(message -> "client".equalsIgnoreCase(message.role())
            || "customer".equalsIgnoreCase(message.role()))
        .map(CustomerMessageSentEvent.ChatMessage::text)
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
  }

  private boolean deferredUntilSend(String field) {
    return Set.of("nextFollowupAt", "nextFollowupDir", "followupNotes").contains(field);
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
