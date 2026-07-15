package com.privateflow.modules.tags;

import java.time.LocalDateTime;
import java.util.List;

public record AutomaticCustomerTagUpdatePlan(
    String analysisKey,
    long customerId,
    String phone,
    int expectedCustomerVersion,
    int effectiveMessageCount,
    String operator,
    LocalDateTime evaluatedAt,
    List<AutomaticCustomerTagDecisionPlan> decisions
) {

  public AutomaticCustomerTagUpdatePlan {
    decisions = decisions == null ? List.of() : List.copyOf(decisions);
  }
}
