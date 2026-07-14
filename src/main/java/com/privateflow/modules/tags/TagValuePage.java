package com.privateflow.modules.tags;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TagValuePage(
    int page,
    int size,
    long total,
    int totalPages,
    List<TagValue> items
) {

  @JsonProperty("values")
  public List<TagValue> values() {
    return items;
  }
}
