package com.privateflow.modules.match;

import java.util.List;

public record MatchResult(
    MatchType matchType,
    List<CustomerSummary> customers,
    int matchCount
) {

  public static MatchResult none() {
    return new MatchResult(MatchType.NONE, List.of(), 0);
  }
}
