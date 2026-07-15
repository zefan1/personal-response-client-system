package com.privateflow.modules.tags;

import java.util.List;

public record CustomerTagUpdateResult(
    int customerVersion,
    boolean updated,
    List<CustomerTagDecisionResult> decisions
) {

  public CustomerTagUpdateResult {
    decisions = decisions == null ? List.of() : List.copyOf(decisions);
  }
}
