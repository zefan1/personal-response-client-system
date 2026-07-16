package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class CustomerAdminSearchRepositoryContractTest {

  @Test
  void exposesUnifiedFilterSearchEntryPoint() {
    assertThatCode(() -> CustomerAdminSearchRepository.class.getMethod(
        "search", CustomerFilter.class, CustomerAccessScope.class))
        .doesNotThrowAnyException();
  }
}
