package com.privateflow.modules.match.config;

import java.util.List;

public record MatchConfig(
    List<String> tagRemovalRules,
    int maxCandidates,
    int fuzzySearchTimeoutMs,
    double confidenceRatioThreshold,
    int confidenceMinLength
) {

  public static MatchConfig defaults() {
    return new MatchConfig(List.of("L1-", "L2-", "A-", "VIP-", "V-"), 5, 2000, 0.5d, 2);
  }
}
