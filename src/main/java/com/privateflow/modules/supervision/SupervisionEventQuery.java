package com.privateflow.modules.supervision;

import java.time.LocalDateTime;

public record SupervisionEventQuery(
    LocalDateTime fromInclusive,
    LocalDateTime toExclusive,
    String operatorUsername,
    String channelCode,
    String leadSource,
    SupervisionEventType eventType,
    int page,
    int pageSize) {

  public SupervisionEventQuery {
    if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
      throw new IllegalArgumentException("a valid date range is required");
    }
    if (page < 1 || pageSize < 1 || pageSize > 100) {
      throw new IllegalArgumentException("page and page size are out of range");
    }
    operatorUsername = normalize(operatorUsername);
    channelCode = normalize(channelCode);
    leadSource = normalize(leadSource);
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
