package com.privateflow.modules.customer.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.CustomerTagsUpdatedEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.config.CustomerCacheProperties;
import com.privateflow.modules.customer.infra.CustomerCacheManager;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.tags.CustomerTagUpdateResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomerQueryServiceImplTest {

  @Test
  void refreshesCustomerCacheAfterTagsUpdated() {
    CustomerCacheManager cacheManager = org.mockito.Mockito.mock(CustomerCacheManager.class);
    CustomerRepository customerRepository = org.mockito.Mockito.mock(CustomerRepository.class);
    CustomerCacheProperties properties = new CustomerCacheProperties();
    Customer customer = new Customer();
    customer.setId(5L);
    customer.setPhone("13900000001");
    customer.setVersion(2);
    when(customerRepository.findByPhone("13900000001")).thenReturn(Optional.of(customer));
    CustomerQueryServiceImpl service = new CustomerQueryServiceImpl(
        cacheManager, customerRepository, properties, 5, 5000);

    service.onCustomerTagsUpdated(new CustomerTagsUpdatedEvent(
        "13900000001",
        5L,
        2,
        "MANUAL",
        new CustomerTagUpdateResult(2, true, List.of())));

    verify(customerRepository).findByPhone("13900000001");
    verify(cacheManager).write(customer);
  }
}
