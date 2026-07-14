package com.privateflow.modules.tags;

import java.math.BigDecimal;

public record TagSelectionContext(
    String evidence,
    int validMessageCount,
    BigDecimal confidence,
    String businessBasis
) {

  public static TagSelectionContext empty() {
    return new TagSelectionContext(null, 0, null, null);
  }
}
