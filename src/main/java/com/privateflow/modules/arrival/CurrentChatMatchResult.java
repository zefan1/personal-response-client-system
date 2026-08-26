package com.privateflow.modules.arrival;

import com.privateflow.modules.match.CustomerSummary;
import java.util.List;

public record CurrentChatMatchResult(String nickname, String matchType, List<CustomerSummary> candidates) {
  public CurrentChatMatchResult {
    candidates = candidates == null ? List.of() : List.copyOf(candidates);
  }
}
