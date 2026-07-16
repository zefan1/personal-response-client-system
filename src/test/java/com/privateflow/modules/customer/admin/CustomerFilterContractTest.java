package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class CustomerFilterContractTest {

  @Test
  void exposesAnImmutableCustomerFilterRecord() {
    assertThatCode(() -> {
      Class<?> filter = Class.forName("com.privateflow.modules.customer.admin.CustomerFilter");
      if (!filter.isRecord()) {
        throw new AssertionError("CustomerFilter must be a record");
      }
    }).doesNotThrowAnyException();
  }
}
