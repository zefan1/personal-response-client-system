package com.privateflow.modules.customer.history;

import java.time.LocalDateTime;

public record CustomerFieldHistoryEntry(
    long id,
    String fieldName,
    String value,
    String source,
    String sourceField,
    String operator,
    LocalDateTime changedAt) {
}
