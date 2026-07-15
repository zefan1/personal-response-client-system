package com.privateflow.modules.tags;

import java.time.LocalDateTime;

public record CustomerTagLockUpdatePlan(
    long customerId,
    String phone,
    int expectedCustomerVersion,
    TagCategory category,
    boolean locked,
    String operator,
    String reason,
    LocalDateTime evaluatedAt
) {
}
