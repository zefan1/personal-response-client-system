package com.privateflow.modules.followup.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.FollowupWsMessageReadyEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.followup.AlertLevel;
import com.privateflow.modules.followup.FollowupItem;
import com.privateflow.modules.followup.FollowupReminderPayload;
import com.privateflow.modules.followup.NewLeadAlertPayload;
import com.privateflow.modules.followup.ReminderType;
import com.privateflow.modules.followup.infra.ReminderLogRepository;
import com.privateflow.modules.followup.infra.TagSuggestionRepository;
import com.privateflow.modules.followup.config.FollowupConfigProvider;
import com.privateflow.modules.match.util.PhoneUtils;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class ActionExecutor {

  private final ObjectMapper objectMapper;
  private final ReminderLogRepository reminderLogRepository;
  private final TagSuggestionRepository tagSuggestionRepository;
  private final FollowupConfigProvider configProvider;
  private final ApplicationEventPublisher eventPublisher;

  public ActionExecutor(
      ObjectMapper objectMapper,
      ReminderLogRepository reminderLogRepository,
      TagSuggestionRepository tagSuggestionRepository,
      FollowupConfigProvider configProvider,
      ApplicationEventPublisher eventPublisher) {
    this.objectMapper = objectMapper;
    this.reminderLogRepository = reminderLogRepository;
    this.tagSuggestionRepository = tagSuggestionRepository;
    this.configProvider = configProvider;
    this.eventPublisher = eventPublisher;
  }

  public void execute(Customer customer, List<RuleMatch> matches) {
    if (customer == null || customer.getPhone() == null || customer.getAssignedKeeper() == null || matches.isEmpty()) {
      return;
    }
    List<FollowupReminderPayload.ReminderPayload> reminders = new ArrayList<>();
    for (RuleMatch match : matches) {
      if (reminderLogRepository.alreadySent(customer.getPhone(), match.rule().id(), LocalDate.now())) {
        continue;
      }
      JsonNode action = readJson(match.rule().actionConfig());
      ReminderType type = reminderType(action, match);
      FollowupItem.TagSuggestionPayload tagSuggestion = null;
      if (match.rule().actionType().name().equals("TAG_CHANGE")) {
        String tagName = action.path("tagName").asText("系统标签建议");
        Long suggestionId = tagSuggestionRepository.upsertPending(
            customer.getPhone(),
            tagName,
            match.rule().id(),
            configProvider.get().tagSuggestionDedupDays());
        if (suggestionId == null) {
          continue;
        }
        type = ReminderType.TAG_SUGGESTION;
        tagSuggestion = new FollowupItem.TagSuggestionPayload(suggestionId, tagName, "SYSTEM");
      }
      reminders.add(new FollowupReminderPayload.ReminderPayload(
          match.rule().id(),
          match.rule().name(),
          type,
          alertLevel(action),
          overdueHours(customer),
          tagSuggestion));
      reminderLogRepository.markSent(customer.getPhone(), match.rule().id(), type);
    }
    if (!reminders.isEmpty()) {
      eventPublisher.publishEvent(new FollowupWsMessageReadyEvent(
          customer.getAssignedKeeper(),
          "FOLLOWUP_REMIND",
          new FollowupReminderPayload(PhoneUtils.mask(customer.getPhone()), reminders)));
    }
  }

  public void executeNewLead(Customer customer, String sourceTable) {
    if (customer == null || customer.getPhone() == null || customer.getAssignedKeeper() == null) {
      return;
    }
    if (reminderLogRepository.alreadySent(customer.getPhone(), 0L, LocalDate.now())) {
      return;
    }
    reminderLogRepository.markSent(customer.getPhone(), 0L, ReminderType.NEW_LEAD);
    eventPublisher.publishEvent(new FollowupWsMessageReadyEvent(
        customer.getAssignedKeeper(),
        "NEW_LEAD_ALERT",
        new NewLeadAlertPayload(
            PhoneUtils.mask(customer.getPhone()),
            customer.getPhone(),
            customer.getNickname(),
            customer.getLeadType(),
            "TUAN_GOU".equals(customer.getLeadType()) ? "HIGH" : "NORMAL",
            sourceTable == null ? customer.getSourceTable() : sourceTable,
            customer.getAssignedKeeper(),
            LocalDateTime.now())));
  }

  private JsonNode readJson(String json) {
    try {
      return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
    } catch (Exception ex) {
      return objectMapper.createObjectNode();
    }
  }

  private ReminderType reminderType(JsonNode action, RuleMatch match) {
    String raw = action.path("reminderType").asText("");
    if (!raw.isBlank()) {
      return ReminderType.valueOf(raw);
    }
    return switch (match.rule().actionType()) {
      case TAG_CHANGE -> ReminderType.TAG_SUGGESTION;
      case NOTIFY_LEADER -> ReminderType.OVERDUE;
      case ALERT -> ReminderType.OVERDUE;
    };
  }

  private AlertLevel alertLevel(JsonNode action) {
    String raw = action.path("alertLevel").asText("NORMAL");
    try {
      return AlertLevel.valueOf(raw);
    } catch (IllegalArgumentException ex) {
      return AlertLevel.NORMAL;
    }
  }

  private Long overdueHours(Customer customer) {
    if (customer.getLastFollowupAt() == null) {
      return null;
    }
    return Duration.between(customer.getLastFollowupAt(), LocalDateTime.now()).toHours();
  }
}
