package com.privateflow.modules.match;

import java.time.LocalDateTime;

public record CustomerSummary(
    String phone,
    String phoneFull,
    String nickname,
    String sourceChannel,
    String leadType,
    String assignedKeeper,
    LocalDateTime lastFollowupAt,
    String intendedStore,
    Confidence confidence,
    Long customerId
) {
  public CustomerSummary(
      String phone,
      String phoneFull,
      String nickname,
      String sourceChannel,
      String leadType,
      String assignedKeeper,
      LocalDateTime lastFollowupAt,
      String intendedStore,
      Confidence confidence) {
    this(phone, phoneFull, nickname, sourceChannel, leadType, assignedKeeper, lastFollowupAt,
        intendedStore, confidence, null);
  }
}
