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

  @Test
  void exposesCountAndExportEntryPoints() {
    assertThatCode(() -> CustomerAdminSearchRepository.class.getMethod(
        "count", CustomerFilter.class, CustomerAccessScope.class))
        .doesNotThrowAnyException();
    assertThatCode(() -> CustomerAdminSearchRepository.class.getMethod(
        "exportRows", CustomerFilter.class, CustomerAccessScope.class, int.class))
        .doesNotThrowAnyException();
  }
}
