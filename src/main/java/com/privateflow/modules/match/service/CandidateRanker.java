package com.privateflow.modules.match.service;

import com.privateflow.modules.match.Confidence;
import com.privateflow.modules.match.CustomerSummary;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CandidateRanker {

  public List<CustomerSummary> rank(List<CustomerSummary> candidates, String currentUser, int limit) {
    Comparator<CustomerSummary> comparator = Comparator
        .comparing((CustomerSummary c) -> ownCustomer(c, currentUser)).reversed()
        .thenComparing(CustomerSummary::lastFollowupAt, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing((CustomerSummary c) -> c.confidence() == Confidence.HIGH ? 0 : 1);
    return candidates.stream()
        .sorted(comparator)
        .limit(limit)
        .toList();
  }

  private boolean ownCustomer(CustomerSummary candidate, String currentUser) {
    return currentUser != null
        && !currentUser.isBlank()
        && candidate.assignedKeeper() != null
        && candidate.assignedKeeper().equalsIgnoreCase(currentUser.trim());
  }
}
