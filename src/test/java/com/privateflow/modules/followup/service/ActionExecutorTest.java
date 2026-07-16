package com.privateflow.modules.followup.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.followup.ActionType;
import com.privateflow.modules.followup.FollowupRule;
import com.privateflow.modules.followup.ReminderType;
import com.privateflow.modules.followup.config.FollowupConfigProvider;
import com.privateflow.modules.followup.infra.ReminderLogRepository;
import com.privateflow.modules.followup.infra.TagSuggestionRepository;
import com.privateflow.modules.tags.TagCandidatePurpose;
import com.privateflow.modules.tags.TagSelectionValidationResult;
import com.privateflow.modules.tags.TagSelectionValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class ActionExecutorTest {

  @Test
  void formalTargetUsesValidatedSuggestionPath() {
    ReminderLogRepository reminderLogs = Mockito.mock(ReminderLogRepository.class);
    TagSuggestionRepository suggestions = Mockito.mock(TagSuggestionRepository.class);
    FollowupConfigProvider config = Mockito.mock(FollowupConfigProvider.class);
    ApplicationEventPublisher events = Mockito.mock(ApplicationEventPublisher.class);
    TagSelectionValidator validator = Mockito.mock(TagSelectionValidator.class);
    TagSelectionValidationResult accepted = Mockito.mock(TagSelectionValidationResult.class);
    when(accepted.accepted()).thenReturn(true);
    when(validator.validateIds(eq(TagCandidatePurpose.FOLLOWUP_RULE), eq(50L), eq(List.of(51L)), any()))
        .thenReturn(accepted);
    when(config.get()).thenReturn(new com.privateflow.modules.followup.config.FollowupConfig(
        "", "", 30, 24, 72, 24, 7, 14, 24, 1000, 120, 1, 7, 600, 48));
    when(suggestions.upsertPending("13800000000", 50L, 51L, "高意向", 9L, 7)).thenReturn(77L);
    ActionExecutor executor = new ActionExecutor(
        new ObjectMapper(), reminderLogs, suggestions, config, events, validator);
    Customer customer = customer();

    executor.execute(customer, List.of(new RuleMatch(new FollowupRule(
        9L, "formal", "{}", ActionType.TAG_CHANGE,
        "{\"tagCategoryId\":50,\"tagValueId\":51,\"tagName\":\"高意向\"}",
        10, true, false, null, null))));

    verify(suggestions).upsertPending("13800000000", 50L, 51L, "高意向", 9L, 7);
    verify(suggestions, never()).upsertPending(eq("13800000000"), eq("高意向"), anyLong(), anyInt());
    verify(reminderLogs).markSent("13800000000", 9L, ReminderType.TAG_SUGGESTION);
  }

  @Test
  void legacyTextTargetKeepsExistingPath() {
    ReminderLogRepository reminderLogs = Mockito.mock(ReminderLogRepository.class);
    TagSuggestionRepository suggestions = Mockito.mock(TagSuggestionRepository.class);
    FollowupConfigProvider config = Mockito.mock(FollowupConfigProvider.class);
    ApplicationEventPublisher events = Mockito.mock(ApplicationEventPublisher.class);
    TagSelectionValidator validator = Mockito.mock(TagSelectionValidator.class);
    when(config.get()).thenReturn(new com.privateflow.modules.followup.config.FollowupConfig(
        "", "", 30, 24, 72, 24, 7, 14, 24, 1000, 120, 1, 7, 600, 48));
    when(suggestions.upsertPending("13800000000", "旧文本", 9L, 7)).thenReturn(78L);
    ActionExecutor executor = new ActionExecutor(
        new ObjectMapper(), reminderLogs, suggestions, config, events, validator);

    executor.execute(customer(), List.of(new RuleMatch(new FollowupRule(
        9L, "legacy", "{}", ActionType.TAG_CHANGE,
        "{\"tagName\":\"旧文本\"}", 10, true, false, null, null))));

    verify(suggestions).upsertPending("13800000000", "旧文本", 9L, 7);
    verify(validator, never()).validateIds(any(), anyLong(), any(), any());
  }

  @Test
  void invalidFormalTargetDoesNotCreateSuggestionOrReminder() {
    ReminderLogRepository reminderLogs = Mockito.mock(ReminderLogRepository.class);
    TagSuggestionRepository suggestions = Mockito.mock(TagSuggestionRepository.class);
    FollowupConfigProvider config = Mockito.mock(FollowupConfigProvider.class);
    ApplicationEventPublisher events = Mockito.mock(ApplicationEventPublisher.class);
    TagSelectionValidator validator = Mockito.mock(TagSelectionValidator.class);
    TagSelectionValidationResult rejected = Mockito.mock(TagSelectionValidationResult.class);
    when(rejected.accepted()).thenReturn(false);
    when(validator.validateIds(eq(TagCandidatePurpose.FOLLOWUP_RULE), eq(50L), eq(List.of(51L)), any()))
        .thenReturn(rejected);
    ActionExecutor executor = new ActionExecutor(
        new ObjectMapper(), reminderLogs, suggestions, config, events, validator);

    executor.execute(customer(), List.of(new RuleMatch(new FollowupRule(
        9L, "invalid", "{}", ActionType.TAG_CHANGE,
        "{\"tagCategoryId\":50,\"tagValueId\":51,\"tagName\":\"高意向\"}",
        10, true, false, null, null))));

    verify(suggestions, never()).upsertPending(any(), anyLong(), anyLong(), any(), anyLong(), anyInt());
    verify(reminderLogs, never()).markSent(any(), anyLong(), any());
  }

  private Customer customer() {
    Customer customer = new Customer();
    customer.setId(7L);
    customer.setPhone("13800000000");
    customer.setAssignedKeeper("keeper-1");
    return customer;
  }
}
