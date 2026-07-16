package com.privateflow.modules.customer.admin;

import java.util.List;

public record TagFilterGroup(
    long categoryId,
    List<Long> valueIds,
    TagMatchMode match) {

  public TagFilterGroup {
    valueIds = valueIds == null ? List.of() : List.copyOf(valueIds);
  }
}
