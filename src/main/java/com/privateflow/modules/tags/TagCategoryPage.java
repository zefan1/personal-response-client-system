package com.privateflow.modules.tags;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TagCategoryPage(
    int page,
    int size,
    long total,
    int totalPages,
    List<TagCategory> items
) {

  @JsonProperty("categories")
  public List<TagCategory> categories() {
    return items;
  }
}
