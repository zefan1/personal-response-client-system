package com.privateflow.modules.tags;

import com.privateflow.modules.skill.TagAnalysisDecision;
import java.util.List;

public record AutomaticCustomerTagUpdateRequest(
    long customerId,
    String phone,
    int expectedCustomerVersion,
    int effectiveMessageCount,
    String operator,
    List<TagAnalysisDecision> decisions
) {

  public AutomaticCustomerTagUpdateRequest {
    decisions = decisions == null ? List.of() : List.copyOf(decisions);
  }
}
