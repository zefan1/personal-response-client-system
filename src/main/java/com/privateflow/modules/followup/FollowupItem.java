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
    LocalDateTime arrivedAt,
    String contactValue,
    String contactType,
    boolean leadProcessed,
    boolean leadInvalid,
    LocalDateTime leadRetainedUntil,
    Integer customerVersion
) {

  public FollowupItem(
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
      LocalDateTime arrivedAt) {
    this(phone, phoneFull, nickname, leadType, lastFollowupAt, nextFollowupAt, nextFollowupDir,
        appointmentDate, appointmentStore, sourceTable, reminderType, overdueHours, alertLevel,
        tagSuggestion, arrivedAt, phoneFull, "PHONE", false, false, null, null);
  }

  public record TagSuggestionPayload(Long suggestionId, String tagName, String confidence) {
  }
}
