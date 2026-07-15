package com.privateflow.modules.tags;

import java.util.List;

public record ManualCustomerTagUpdateRequest(
    Integer version,
    List<Long> tagValueIds,
    String reason
) {

  public ManualCustomerTagUpdateRequest {
    tagValueIds = tagValueIds == null ? List.of() : List.copyOf(tagValueIds);
  }
}
