package com.privateflow.modules.customer.admin;

import java.time.LocalDateTime;
import java.util.List;

public record IntentProjectMappingRule(
    long id,
    String optionId,
    String optionText,
    List<String> keywords,
    int priority,
    String status,
    String sourceField,
    LocalDateTime lastSeenAt,
    LocalDateTime updatedAt) {

  public IntentProjectMappingRule {
    optionId = optionId == null ? "" : optionId.trim();
    optionText = optionText == null ? "" : optionText.trim();
    keywords = keywords == null ? List.of() : keywords.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
    status = status == null ? "ACTIVE" : status.trim().toUpperCase();
    sourceField = sourceField == null || sourceField.isBlank() ? "意向项目" : sourceField.trim();
  }
}
