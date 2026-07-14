package com.privateflow.modules.tags;

import java.util.Objects;

public record TagValueCode(String categoryKey, String valueCode) {

  public TagValueCode {
    Objects.requireNonNull(categoryKey, "categoryKey");
    Objects.requireNonNull(valueCode, "valueCode");
  }
}
