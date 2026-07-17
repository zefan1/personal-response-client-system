package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerAdminSearchServiceTest {

  @Test
  void validatesStructuredRequestAndPassesResolvedScopeToRepository() {
    CustomerAdminSearchRepository repository = mock(CustomerAdminSearchRepository.class);
    CustomerFilterValidator validator = mock(CustomerFilterValidator.class);
    CustomerAccessScopeResolver scopeResolver = mock(CustomerAccessScopeResolver.class);
    CustomerCsvWriter csvWriter = mock(CustomerCsvWriter.class);
    CustomerAdminSearchService service = new CustomerAdminSearchService(repository, validator, scopeResolver, csvWriter);
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

  @Test
  void legacySearchUsesValidatedFilterAndCurrentScope() {
    CustomerAdminSearchRepository repository = mock(CustomerAdminSearchRepository.class);
    CustomerFilterValidator validator = mock(CustomerFilterValidator.class);
    CustomerAccessScopeResolver scopeResolver = mock(CustomerAccessScopeResolver.class);
    CustomerCsvWriter csvWriter = mock(CustomerCsvWriter.class);
    CustomerAdminSearchService service = new CustomerAdminSearchService(repository, validator, scopeResolver, csvWriter);
    CustomerFilter normalized = CustomerFilter.empty();
    CustomerAccessScope scope = new CustomerAccessScope(false, List.of("keeper-1"), true);
    when(validator.validate(org.mockito.ArgumentMatchers.any(CustomerFilter.class))).thenReturn(normalized);
    when(scopeResolver.currentScope()).thenReturn(scope);
    when(repository.search(normalized, scope)).thenReturn(new CustomerAdminSearchPage(List.of(), 0, 1, 20, 1));

    service.search("1111", 1, 20);

    verify(validator).validate(org.mockito.ArgumentMatchers.any(CustomerFilter.class));
    verify(scopeResolver).currentScope();
    verify(repository).search(normalized, scope);
  }

  @Test
  void exportsValidatedRowsForCurrentScope() {
    CustomerAdminSearchRepository repository = mock(CustomerAdminSearchRepository.class);
    CustomerFilterValidator validator = mock(CustomerFilterValidator.class);
    CustomerAccessScopeResolver scopeResolver = mock(CustomerAccessScopeResolver.class);
    CustomerCsvWriter csvWriter = mock(CustomerCsvWriter.class);
    CustomerAdminSearchService service = new CustomerAdminSearchService(repository, validator, scopeResolver, csvWriter);
    CustomerSearchRequest request = new CustomerSearchRequest(
        "Alice", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        null, null, List.of(), TagGroupLogic.AND, CustomerSortField.UPDATED_AT,
        SortDirection.DESC, null, null);
    CustomerFilter filter = request.toFilter();
    CustomerAccessScope scope = new CustomerAccessScope(false, List.of("keeper-1"), true);
    CustomerAdminListItem item = new CustomerAdminListItem(
        1L, "13800000001", "Alice", "企微", "GENERAL", "keeper-1", "万江店", "产后修复",
        "待跟进", "MEDIUM", null, null, null, null, null, null, "customers", null);
    List<CustomerAdminListItem> rows = List.of(item);
    byte[] csv = "\uFEFF客户ID,手机号\r\n1,13800000001\r\n".getBytes(StandardCharsets.UTF_8);
    when(validator.validate(filter)).thenReturn(filter);
    when(scopeResolver.currentScope()).thenReturn(scope);
    when(repository.count(filter, scope)).thenReturn(1L);
    when(repository.exportRows(filter, scope, 1)).thenReturn(rows);
    when(csvWriter.write(rows)).thenReturn(csv);

    assertThat(service.export(request)).isEqualTo(csv);

    verify(repository).count(filter, scope);
    verify(repository).exportRows(filter, scope, 1);
    verify(csvWriter).write(rows);
  }

  @Test
  void rejectsExportsAboveMaximumRowLimit() {
    CustomerAdminSearchRepository repository = mock(CustomerAdminSearchRepository.class);
    CustomerFilterValidator validator = mock(CustomerFilterValidator.class);
    CustomerAccessScopeResolver scopeResolver = mock(CustomerAccessScopeResolver.class);
    CustomerCsvWriter csvWriter = mock(CustomerCsvWriter.class);
    CustomerAdminSearchService service = new CustomerAdminSearchService(repository, validator, scopeResolver, csvWriter);
    CustomerSearchRequest request = new CustomerSearchRequest(
        "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        null, null, List.of(), TagGroupLogic.AND, CustomerSortField.UPDATED_AT,
        SortDirection.DESC, null, null);
    CustomerFilter filter = request.toFilter();
    CustomerAccessScope scope = CustomerAccessScope.all();
    when(validator.validate(filter)).thenReturn(filter);
    when(scopeResolver.currentScope()).thenReturn(scope);
    when(repository.count(filter, scope)).thenReturn(10_001L);

    assertThatThrownBy(() -> service.export(request))
        .isInstanceOf(ApiException.class)
        .hasMessage("导出客户数量超过 10000 条，请缩小筛选范围");
  }
}
