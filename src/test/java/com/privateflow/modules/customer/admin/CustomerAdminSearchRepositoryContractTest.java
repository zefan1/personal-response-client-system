package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

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

  @Test
  void marksTheProductionConstructorForSpringInjection() throws Exception {
    assertThat(CustomerAdminSearchRepository.class.getConstructor(
        JdbcTemplate.class, CustomerFilterQueryBuilder.class).isAnnotationPresent(Autowired.class))
        .isTrue();
  }
}
