package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.history.CustomerFieldHistoryEntry;
import com.privateflow.modules.customer.history.CustomerFieldHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomerMasterRecordServiceTest {

  @Test
  void returnsTheApprovedBusinessFieldsWithTheirCurrentValues() {
    CustomerAdminSearchRepository repository = mock(CustomerAdminSearchRepository.class);
    CustomerAccessScopeResolver scopeResolver = mock(CustomerAccessScopeResolver.class);
    CustomerAccessScope scope = CustomerAccessScope.all();
    Customer customer = customer();
    when(scopeResolver.currentScope()).thenReturn(scope);
    when(repository.findByIdInScope(42L, scope)).thenReturn(Optional.of(customer));
    CustomerMasterRecordService service = new CustomerMasterRecordService(repository, scopeResolver);

    CustomerMasterRecord record = service.record(42L);

    assertThat(record.customer().nickname()).isEqualTo("王女士");
    assertThat(record.fields()).anySatisfy(field -> {
      assertThat(field.fieldName()).isEqualTo("customerName");
      assertThat(field.label()).isEqualTo("客户姓名");
    });
    assertThat(record.fields()).anySatisfy(field -> {
      assertThat(field.fieldName()).isEqualTo("phone");
      assertThat(field.label()).isEqualTo("手机号");
      assertThat(field.value()).isEqualTo("13800000042");
    });
    assertThat(record.fields()).anySatisfy(field -> {
      assertThat(field.fieldName()).isEqualTo("internalNote");
      assertThat(field.value()).isNull();
    });
    assertThat(record.fields()).extracting(CustomerMasterFieldValue::fieldName)
        .doesNotContain("worries", "leadCaptureType", "updatedAt", "arrivalSourceRowId");
    verify(repository).findByIdInScope(42L, scope);
  }

  @Test
  void searchReturnsOnlyScopedCandidates() {
    CustomerAdminSearchRepository repository = mock(CustomerAdminSearchRepository.class);
    CustomerAccessScopeResolver scopeResolver = mock(CustomerAccessScopeResolver.class);
    CustomerAccessScope scope = new CustomerAccessScope(false, List.of("keeper-1"), true);
    when(scopeResolver.currentScope()).thenReturn(scope);
    when(repository.searchMasterCandidates("王", scope, 20)).thenReturn(List.of(customer()));
    CustomerMasterRecordService service = new CustomerMasterRecordService(repository, scopeResolver);

    assertThat(service.search(" 王 ")).singleElement().satisfies(candidate -> {
      assertThat(candidate.id()).isEqualTo(42L);
      assertThat(candidate.wechatId()).isEqualTo("wx-wang");
    });
    verify(repository).searchMasterCandidates("王", scope, 20);
  }

  @Test
  void exposesTheLatestRecordedSourceAndReturnsTheFieldHistory() {
    CustomerAdminSearchRepository repository = mock(CustomerAdminSearchRepository.class);
    CustomerAccessScopeResolver scopeResolver = mock(CustomerAccessScopeResolver.class);
    CustomerFieldHistoryRepository historyRepository = mock(CustomerFieldHistoryRepository.class);
    CustomerAccessScope scope = CustomerAccessScope.all();
    Customer customer = customer();
    when(scopeResolver.currentScope()).thenReturn(scope);
    when(repository.findByIdInScope(42L, scope)).thenReturn(Optional.of(customer));
    CustomerFieldHistoryEntry latest = new CustomerFieldHistoryEntry(
        7L, "nickname", "王女士", "人工编辑", "后台客户档案 · nickname", "keeper-1",
        LocalDateTime.of(2026, 8, 18, 9, 20));
    when(historyRepository.latestByCustomer(42L)).thenReturn(Map.of("nickname", latest));
    when(historyRepository.list(42L, "nickname")).thenReturn(List.of(latest));
    CustomerMasterRecordService service = new CustomerMasterRecordService(
        repository, scopeResolver, historyRepository);

    CustomerMasterRecord record = service.record(42L);

    assertThat(record.fields()).anySatisfy(field -> {
      assertThat(field.fieldName()).isEqualTo("nickname");
      assertThat(field.source()).isEqualTo("人工编辑");
      assertThat(field.sourceField()).isEqualTo("后台客户档案 · nickname");
    });
    assertThat(service.history(42L, "nickname")).containsExactly(latest);
    verify(historyRepository).list(42L, "nickname");
  }

  private Customer customer() {
    Customer customer = new Customer();
    customer.setId(42L);
    customer.setNickname("王女士");
    customer.setCustomerName("王小红");
    customer.setPhone("13800000042");
    customer.setWechatId("wx-wang");
    customer.setUpdatedAt(LocalDateTime.of(2026, 8, 17, 10, 0));
    return customer;
  }
}
