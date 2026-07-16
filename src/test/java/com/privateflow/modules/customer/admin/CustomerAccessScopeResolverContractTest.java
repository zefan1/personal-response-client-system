package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class CustomerAccessScopeResolverContractTest {

  @Test
  void exposesCurrentAccessScope() {
    assertThatCode(() -> {
      Class<?> resolver = Class.forName(
          "com.privateflow.modules.customer.admin.CustomerAccessScopeResolver");
      resolver.getMethod("currentScope");
    }).doesNotThrowAnyException();
  }
}
