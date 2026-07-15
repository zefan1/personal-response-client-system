package com.privateflow.modules.tags;

import java.time.LocalDateTime;
import java.util.List;

public record ManualCustomerTagUpdatePlan(
    long customerId,
    String phone,
    int expectedCustomerVersion,
    TagCategory category,
    List<TagValue> desiredValues,
    List<CustomerTagAssignment> previousAssignments,
    String operator,
    String reason,
    boolean lockAfterUpdate,
    LocalDateTime evaluatedAt
) {

  public ManualCustomerTagUpdatePlan {
    desiredValues = desiredValues == null ? List.of() : List.copyOf(desiredValues);
    previousAssignments = previousAssignments == null ? List.of() : List.copyOf(previousAssignments);
  }
}
