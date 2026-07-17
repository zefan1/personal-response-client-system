package com.privateflow.modules.tags;

import java.util.List;

public record TagExchangeUnmatchedValue(
    String boundField,
    String rawValue,
    List<String> unmatchedTokens,
    Long categoryId,
    TagExchangeSourceType sourceType,
    String sourceRecordId) {

  public TagExchangeUnmatchedValue {
    unmatchedTokens = unmatchedTokens == null ? List.of() : List.copyOf(unmatchedTokens);
  }
}
