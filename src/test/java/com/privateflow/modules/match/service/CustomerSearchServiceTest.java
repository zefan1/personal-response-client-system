package com.privateflow.modules.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.match.CustomerSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CustomerSearchServiceTest {

  @Test
  void shortPhoneSuffixSearchUsesKeywordSearchAndReturnsFullPhone() {
    CustomerQueryService queryService = Mockito.mock(CustomerQueryService.class);
    CustomerAccessService accessService = Mockito.mock(CustomerAccessService.class);
    CustomerSearchService service = new CustomerSearchService(queryService, new CustomerSummaryMapper(), accessService);
    Customer customer = new Customer();
    customer.setPhone("18800001111");
    customer.setNickname("逾期跟进客户");
    customer.setLeadType("TUAN_GOU");

    when(queryService.searchByKeyword("1111", 10)).thenReturn(List.of(customer));
    when(accessService.canAccess(customer)).thenReturn(true);

    CustomerSearchResult result = service.search("1111", 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.customers()).hasSize(1);
    assertThat(result.customers().get(0).phone()).isEqualTo("188****1111");
    assertThat(result.customers().get(0).phoneFull()).isEqualTo("18800001111");
    verify(queryService).searchByKeyword("1111", 10);
  }

  @Test
  void searchSkipsMaskedPhoneRowsFromDirtyLocalFixtures() {
    CustomerQueryService queryService = Mockito.mock(CustomerQueryService.class);
    CustomerAccessService accessService = Mockito.mock(CustomerAccessService.class);
    CustomerSearchService service = new CustomerSearchService(queryService, new CustomerSummaryMapper(), accessService);
    Customer valid = new Customer();
    valid.setPhone("18800001111");
    valid.setNickname("逾期跟进客户");
    valid.setLeadType("TUAN_GOU");
    Customer masked = new Customer();
    masked.setPhone("****1111");
    masked.setLeadType("TUAN_GOU");

    when(queryService.searchByKeyword("1111", 10)).thenReturn(List.of(valid, masked));
    when(accessService.canAccess(valid)).thenReturn(true);
    when(accessService.canAccess(masked)).thenReturn(true);

    CustomerSearchResult result = service.search("1111", 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.customers()).extracting("phoneFull").containsExactly("18800001111");
  }

  @Test
  void searchHidesCustomersOutsideCurrentAccountScope() {
    CustomerQueryService queryService = Mockito.mock(CustomerQueryService.class);
    CustomerAccessService accessService = Mockito.mock(CustomerAccessService.class);
    CustomerSearchService service = new CustomerSearchService(queryService, new CustomerSummaryMapper(), accessService);
    Customer own = new Customer();
    own.setPhone("18800001111");
    own.setNickname("自己的客户");
    own.setLeadType("TUAN_GOU");
    Customer other = new Customer();
    other.setPhone("18800002222");
    other.setNickname("别人的客户");
    other.setLeadType("XIAN_SUO");

    when(queryService.searchByKeyword("客户", 10)).thenReturn(List.of(own, other));
    when(accessService.canAccess(own)).thenReturn(true);
    when(accessService.canAccess(other)).thenReturn(false);

    CustomerSearchResult result = service.search("客户", 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.customers()).extracting("phoneFull").containsExactly("18800001111");
  }
}
