package com.privateflow.modules.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.CustomerTableSyncRequestedEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.profile.CustomerPhoneAssignmentRequest;
import com.privateflow.modules.profile.CustomerPhoneAssignmentResult;
import com.privateflow.modules.profile.ProfileErrorCodes;
import com.privateflow.modules.profile.ProfileUpdateException;
import com.privateflow.modules.profile.infra.AuditLogRepository;
import com.privateflow.modules.profile.infra.ProfileWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CustomerPhoneAssignmentServiceTest {

  @Test
  void assignsPhoneToPhoneLessCustomerAndRequestsTableSync() {
    CustomerQueryService customers = mock(CustomerQueryService.class);
    CustomerAccessService access = mock(CustomerAccessService.class);
    ProfileWriter writer = mock(ProfileWriter.class);
    AuditLogRepository audit = mock(AuditLogRepository.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    Customer customer = customer(56L, null, 1);
    when(customers.getById(56L)).thenReturn(customer);
    when(customers.getByPhone("13434567622")).thenReturn(null);
    when(access.canAccess(customer)).thenReturn(true);
    when(writer.writeByCustomerId(56L, Map.of("phone", "13434567622"), 1, false)).thenReturn(2);

    CustomerPhoneAssignmentService service = new CustomerPhoneAssignmentService(
        customers, access, writer, audit, events);

    CustomerPhoneAssignmentResult result = service.assign(
        56L, new CustomerPhoneAssignmentRequest("13434567622", 1, "operator"));

    assertThat(result.customerId()).isEqualTo(56L);
    assertThat(result.version()).isEqualTo(2);
    verify(writer).writeByCustomerId(56L, Map.of("phone", "13434567622"), 1, false);
    verify(events).publishEvent(new CustomerTableSyncRequestedEvent(56L));
    verify(audit).log("ASSIGN_CUSTOMER_PHONE", "operator", "customer", "56", "manual phone assignment");
  }

  @Test
  void rejectsPhoneAlreadyOwnedByAnotherCustomer() {
    CustomerQueryService customers = mock(CustomerQueryService.class);
    CustomerAccessService access = mock(CustomerAccessService.class);
    Customer current = customer(56L, null, 1);
    when(customers.getById(56L)).thenReturn(current);
    when(access.canAccess(current)).thenReturn(true);
    when(customers.getByPhone("13434567622")).thenReturn(customer(99L, "13434567622", 1));
    CustomerPhoneAssignmentService service = new CustomerPhoneAssignmentService(
        customers, access, mock(ProfileWriter.class), mock(AuditLogRepository.class),
        mock(ApplicationEventPublisher.class));

    assertThatThrownBy(() -> service.assign(
        56L, new CustomerPhoneAssignmentRequest("13434567622", 1, "operator")))
        .isInstanceOf(ProfileUpdateException.class)
        .extracting("errorCode")
        .isEqualTo(ProfileErrorCodes.BAD_REQUEST);
  }

  private Customer customer(long id, String phone, int version) {
    Customer customer = new Customer();
    customer.setId(id);
    customer.setPhone(phone);
    customer.setVersion(version);
    return customer;
  }
}
