package com.privateflow.common.events;

import java.util.List;

public record ProfileSuggestionsReadyEvent(
    String phone,
    int suggestionCount,
    List<SuggestionPayload> suggestions
) {

  public record SuggestionPayload(String fieldName, Object currentValue, Object suggestedValue) {
  }
}
