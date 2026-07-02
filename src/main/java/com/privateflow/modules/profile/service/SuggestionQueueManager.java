package com.privateflow.modules.profile.service;

import com.privateflow.common.events.ProfileSuggestionsReadyEvent;
import com.privateflow.common.events.ProfileUpdatedEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.profile.BatchResolveRequest;
import com.privateflow.modules.profile.BatchResolveResult;
import com.privateflow.modules.profile.ProfileErrorCodes;
import com.privateflow.modules.profile.ProfileSuggestion;
import com.privateflow.modules.profile.ProfileUpdateException;
import com.privateflow.modules.profile.ResolveAction;
import com.privateflow.modules.profile.SuggestionStatus;
import com.privateflow.modules.profile.config.ProfileConfigProvider;
import com.privateflow.modules.profile.infra.AuditLogRepository;
import com.privateflow.modules.profile.infra.ProfileFieldRegistry;
import com.privateflow.modules.profile.infra.ProfileWriter;
import com.privateflow.modules.profile.infra.SuggestionRepository;
import com.privateflow.modules.skill.FieldUpdate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SuggestionQueueManager {

  private final SuggestionRepository suggestionRepository;
  private final CustomerQueryService customerQueryService;
  private final ProfileFieldRegistry fieldRegistry;
  private final ProfileWriter profileWriter;
  private final ProfileConfigProvider configProvider;
  private final ApplicationEventPublisher eventPublisher;
  private final AuditLogRepository auditLogRepository;

  public SuggestionQueueManager(
      SuggestionRepository suggestionRepository,
      CustomerQueryService customerQueryService,
      ProfileFieldRegistry fieldRegistry,
      ProfileWriter profileWriter,
      ProfileConfigProvider configProvider,
      ApplicationEventPublisher eventPublisher,
      AuditLogRepository auditLogRepository) {
    this.suggestionRepository = suggestionRepository;
    this.customerQueryService = customerQueryService;
    this.fieldRegistry = fieldRegistry;
    this.profileWriter = profileWriter;
    this.configProvider = configProvider;
    this.eventPublisher = eventPublisher;
    this.auditLogRepository = auditLogRepository;
  }

  public List<ProfileSuggestion> listPending(String phone) {
    return suggestionRepository.findPending(phone, List.of(), configProvider.get().suggestionMaxPerCustomer());
  }

  public void enqueue(String phone, Customer customer, Map<String, FieldUpdate> mediumFields) {
    if (mediumFields == null || mediumFields.isEmpty()) {
      return;
    }
    List<ProfileSuggestionsReadyEvent.SuggestionPayload> payloads = new ArrayList<>();
    mediumFields.forEach((fieldName, update) -> {
      Object currentValue = fieldRegistry.readValue(customer, fieldName);
      suggestionRepository.upsertPending(phone, fieldName, currentValue, update.value(), "MEDIUM");
      payloads.add(new ProfileSuggestionsReadyEvent.SuggestionPayload(fieldName, currentValue, update.value()));
    });
    int overflow = suggestionRepository.countPending(phone) - configProvider.get().suggestionMaxPerCustomer();
    if (overflow > 0) {
      suggestionRepository.rejectOldestPending(phone, overflow);
    }
    eventPublisher.publishEvent(new ProfileSuggestionsReadyEvent(phone, payloads.size(), payloads));
  }

  public BatchResolveResult batchResolve(String phone, BatchResolveRequest request) {
    if (request == null || request.action() == null) {
      throw new ProfileUpdateException(ProfileErrorCodes.BAD_REQUEST, "action 参数不合法");
    }
    try {
      List<ProfileSuggestion> pending = suggestionRepository.findPending(
          phone,
          request.suggestionIds(),
          configProvider.get().suggestionMaxPerCustomer());
      if (pending.isEmpty()) {
        return new BatchResolveResult(0, 0, 0);
      }
      if (request.action() == ResolveAction.REJECT) {
        int rejected = suggestionRepository.markStatus(ids(pending), SuggestionStatus.REJECTED);
        auditLogRepository.log("UPDATE_PROFILE", request.operator(), "customer", phone, "reject profile suggestions");
        return new BatchResolveResult(0, rejected, pending.size() - rejected);
      }
      if (request.action() != ResolveAction.CONFIRM) {
        throw new ProfileUpdateException(ProfileErrorCodes.BAD_REQUEST, "action 参数不合法");
      }
      return confirm(phone, pending, request.operator());
    } catch (ProfileUpdateException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new ProfileUpdateException(ProfileErrorCodes.BATCH_RESOLVE_FAILED, "档案更新建议批量处理失败", ex);
    }
  }

  private BatchResolveResult confirm(String phone, List<ProfileSuggestion> pending, String operator) {
    Customer customer = customerQueryService.getByPhone(phone);
    if (customer == null) {
      throw new ProfileUpdateException(ProfileErrorCodes.BAD_REQUEST, "客户不存在");
    }
    Map<String, Object> updates = new LinkedHashMap<>();
    List<Long> confirmedIds = new ArrayList<>();
    List<Long> skippedIds = new ArrayList<>();
    for (ProfileSuggestion suggestion : pending) {
      if (!fieldRegistry.supports(suggestion.fieldName())) {
        skippedIds.add(suggestion.id());
        continue;
      }
      Object currentValue = fieldRegistry.readValue(customer, suggestion.fieldName());
      String current = currentValue == null ? null : String.valueOf(currentValue);
      String expected = suggestion.currentValue() == null ? null : String.valueOf(suggestion.currentValue());
      if (expected != null && current != null && !expected.equals(current)) {
        skippedIds.add(suggestion.id());
        continue;
      }
      updates.put(suggestion.fieldName(), suggestion.suggestedValue());
      confirmedIds.add(suggestion.id());
    }
    int confirmed = 0;
    if (!updates.isEmpty()) {
      profileWriter.write(phone, updates, customer.getVersion(), false);
      eventPublisher.publishEvent(new ProfileUpdatedEvent(phone, List.copyOf(updates.keySet())));
      confirmed = suggestionRepository.markStatus(confirmedIds, SuggestionStatus.CONFIRMED);
    }
    int skipped = suggestionRepository.markStatus(skippedIds, SuggestionStatus.CONFLICT_SKIPPED);
    auditLogRepository.log("UPDATE_PROFILE", operator, "customer", phone, "confirm profile suggestions");
    return new BatchResolveResult(confirmed, 0, skipped);
  }

  @Scheduled(cron = "${profile.suggestion-cleanup-cron:0 0 3 * * *}")
  public void cleanExpired() {
    suggestionRepository.rejectExpired(configProvider.get().suggestionExpireDays());
  }

  private List<Long> ids(List<ProfileSuggestion> suggestions) {
    return suggestions.stream().map(ProfileSuggestion::id).toList();
  }
}
