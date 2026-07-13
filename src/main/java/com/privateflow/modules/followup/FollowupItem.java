package com.privateflow.modules.followup;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FollowupItem(
    String phone,
    String phoneFull,
    String nickname,
    String leadType,
    LocalDateTime lastFollowupAt,
    LocalDateTime nextFollowupAt,
    String nextFollowupDir,
    LocalDate appointmentDate,
    String appointmentStore,
    String sourceTable,
    ReminderType reminderType,
    Long overdueHours,
    AlertLevel alertLevel,
    TagSuggestionPayload tagSuggestion,
    LocalDateTime arrivedAt
) {

  public record TagSuggestionPayload(Long suggestionId, String tagName, String confidence) {
  }
}
