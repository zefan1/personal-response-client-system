package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class CustomerAdminSearchApiContractTest {

  @Test
  void exposesStructuredSearchRequestAndPostControllerMethod() {
    assertThatCode(() -> {
      Class<?> request = Class.forName(
          "com.privateflow.modules.customer.admin.CustomerSearchRequest");
      CustomerAdminSearchController.class.getMethod("search", request);
    }).doesNotThrowAnyException();
  }
}
