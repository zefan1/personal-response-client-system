package com.privateflow.modules.supervision;

import java.time.LocalDateTime;

public record SupervisionMetricsQuery(
    LocalDateTime fromInclusive,
    LocalDateTime toExclusive,
    String operatorUsername,
    String channelCode,
    String leadSource) {

  public SupervisionMetricsQuery {
    if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
      throw new IllegalArgumentException("a valid date range is required");
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
