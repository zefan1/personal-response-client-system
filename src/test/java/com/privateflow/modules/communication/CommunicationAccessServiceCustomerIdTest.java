package com.privateflow.modules.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommunicationAccessServiceCustomerIdTest {

  @Test
  void listsMessagesForPhoneLessCustomerByCustomerId() {
    CommunicationArchiveRepository repository = mock(CommunicationArchiveRepository.class);
    CustomerQueryService customers = mock(CustomerQueryService.class);
    CustomerAccessService access = mock(CustomerAccessService.class);
    Customer customer = new Customer();
    customer.setId(44L);
    customer.setNickname("仅昵称");
    when(customers.getById(44L)).thenReturn(customer);
    when(access.canAccess(customer)).thenReturn(true);
    when(repository.searchCustomerMessages(eq(44L), eq(null), any(), any(), eq(null), eq(null), eq(51)))
        .thenReturn(List.of());
    CommunicationAccessService service = new CommunicationAccessService(repository, customers, access);

    CommunicationMessagePage page = service.listMessages(44L, null, LocalDate.of(2026, 8, 1), null, null, null, 50);

    assertThat(page.messages()).isEmpty();
  }
}
