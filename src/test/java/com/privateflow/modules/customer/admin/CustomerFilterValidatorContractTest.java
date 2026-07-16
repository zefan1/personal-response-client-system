package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class CustomerFilterValidatorContractTest {

  @Test
  void exposesValidationEntryPoint() {
    assertThatCode(() -> {
      Class<?> validator = Class.forName(
          "com.privateflow.modules.customer.admin.CustomerFilterValidator");
      validator.getMethod("validate", CustomerFilter.class);
    }).doesNotThrowAnyException();
  }
}
