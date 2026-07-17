package com.privateflow.modules.tags;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TagExchangeResult(
    Map<String, Object> acceptedFields,
    List<String> filteredFields,
    List<TagExchangeUnmatchedValue> unmatched) {

  public TagExchangeResult {
    acceptedFields = acceptedFields == null
        ? Map.of()
        : Map.copyOf(new LinkedHashMap<>(acceptedFields));
    filteredFields = filteredFields == null ? List.of() : List.copyOf(filteredFields);
    unmatched = unmatched == null ? List.of() : List.copyOf(unmatched);
  }
}
