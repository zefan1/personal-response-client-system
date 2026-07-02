package com.privateflow.modules.followup;

import java.util.List;

public record FollowupReminderPayload(String phone, List<ReminderPayload> reminders) {

  public record ReminderPayload(
      Long ruleId,
      String ruleName,
      ReminderType reminderType,
      AlertLevel alertLevel,
      Long overdueHours,
      FollowupItem.TagSuggestionPayload tagSuggestion
  ) {
  }
}
