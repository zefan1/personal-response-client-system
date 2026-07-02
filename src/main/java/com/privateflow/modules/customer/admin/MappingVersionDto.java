package com.privateflow.modules.customer.admin;

import java.time.LocalDateTime;

public record MappingVersionDto(
    int version,
    int mappingCount,
    String changedBy,
    LocalDateTime changedAt,
    String summary
) {
}
