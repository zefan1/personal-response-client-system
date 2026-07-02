package com.privateflow.modules.profile;

import java.time.LocalDateTime;

public record ProfileSuggestion(
    Long id,
    String phone,
    String fieldName,
    Object currentValue,
    Object suggestedValue,
    String confidence,
    SuggestionStatus status,
    LocalDateTime createdAt,
    LocalDateTime resolvedAt
) {
}
