package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class CustomerFilterQueryBuilderContractTest {

  @Test
  void exposesParameterizedQuerySpecEntryPoint() {
    assertThatCode(() -> {
      Class<?> scope = Class.forName(
          "com.privateflow.modules.customer.admin.CustomerAccessScope");
      Class<?> builder = Class.forName(
          "com.privateflow.modules.customer.admin.CustomerFilterQueryBuilder");
      builder.getMethod("build", CustomerFilter.class, scope);
    }).doesNotThrowAnyException();
  }
}
