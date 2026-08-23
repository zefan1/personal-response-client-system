package com.privateflow.modules.followup;

import java.time.LocalDateTime;

public record NewLeadAlertPayload(
    String phone,
    String phoneFull,
    String nickname,
    String leadType,
    String priority,
    String sourceTable,
    String assignedKeeper,
    LocalDateTime arrivedAt,
    String contactValue,
    String contactType,
    boolean leadProcessed,
    boolean leadInvalid,
    LocalDateTime leadRetainedUntil,
    Integer customerVersion
) {

  public NewLeadAlertPayload(
      String phone,
      String phoneFull,
      String nickname,
      String leadType,
      String priority,
      String sourceTable,
      String assignedKeeper,
      LocalDateTime arrivedAt) {
    this(phone, phoneFull, nickname, leadType, priority, sourceTable, assignedKeeper, arrivedAt,
        phoneFull, "PHONE", false, false, null, null);
  }
}
