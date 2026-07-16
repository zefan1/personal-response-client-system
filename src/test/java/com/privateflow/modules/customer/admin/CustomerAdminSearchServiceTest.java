package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerAdminSearchServiceTest {

  @Test
  void validatesStructuredRequestAndPassesResolvedScopeToRepository() {
    CustomerAdminSearchRepository repository = mock(CustomerAdminSearchRepository.class);
    CustomerFilterValidator validator = mock(CustomerFilterValidator.class);
    CustomerAccessScopeResolver scopeResolver = mock(CustomerAccessScopeResolver.class);
    CustomerAdminSearchService service = new CustomerAdminSearchService(repository, validator, scopeResolver);
    CustomerSearchRequest request = new CustomerSearchRequest(
        "Alice", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        null, null, List.of(), TagGroupLogic.AND, CustomerSortField.UPDATED_AT,
        SortDirection.DESC, 1, 20);
    CustomerFilter filter = request.toFilter();
    CustomerAccessScope scope = new CustomerAccessScope(false, List.of("keeper-1"), true);
    CustomerAdminSearchPage expected = new CustomerAdminSearchPage(List.of(), 0, 1, 20, 1);
    when(validator.validate(filter)).thenReturn(filter);
    when(scopeResolver.currentScope()).thenReturn(scope);
    when(repository.search(filter, scope)).thenReturn(expected);

    CustomerAdminSearchPage actual = service.search(request);

    assertThat(actual).isSameAs(expected);
    verify(validator).validate(filter);
    verify(scopeResolver).currentScope();
    verify(repository).search(filter, scope);
  }
}
