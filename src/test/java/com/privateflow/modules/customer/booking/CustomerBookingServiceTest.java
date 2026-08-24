package com.privateflow.modules.customer.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.privateflow.modules.arrival.ArrivalHandoverService;
import com.privateflow.modules.arrival.ArrivalHandoverTaskDecision;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.profile.infra.ProfileWriter;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomerBookingServiceTest {

  @Test
  void completedDuplicateDoesNotReturnCustomerToPendingSupplement() {
    CustomerQueryService customers = mock(CustomerQueryService.class);
    ProfileWriter writer = mock(ProfileWriter.class);
    ArrivalHandoverService handover = mock(ArrivalHandoverService.class);
    Customer customer = customer();
    when(customers.getByPhone(customer.getPhone())).thenReturn(customer);
    when(handover.createOrRefresh(eq(customer), eq("2026-08-31"), eq("14:30"), eq("东城店"), eq("腹直肌检测")))
        .thenReturn(new ArrivalHandoverTaskDecision(1L, true));

    BookingConfirmResult result = new CustomerBookingService(customers, writer, handover)
        .confirm(customer.getPhone(), new BookingConfirmRequest("2026-08-31", "14:30", "东城店", "腹直肌检测"));

    assertThat(result.appointmentStatus()).isEqualTo("已预约");
    verifyNoInteractions(writer);
  }

  @Test
  void newAppointmentWritesPendingSupplementState() {
    CustomerQueryService customers = mock(CustomerQueryService.class);
    ProfileWriter writer = mock(ProfileWriter.class);
    ArrivalHandoverService handover = mock(ArrivalHandoverService.class);
    Customer customer = customer();
    when(customers.getByPhone(customer.getPhone())).thenReturn(customer);
    when(handover.createOrRefresh(any(), any(), any(), any(), any()))
        .thenReturn(new ArrivalHandoverTaskDecision(2L, false));

    BookingConfirmResult result = new CustomerBookingService(customers, writer, handover)
        .confirm(customer.getPhone(), new BookingConfirmRequest("2026-09-01", "10:00", "东城店", "盆底评估"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
    verify(writer).write(eq(customer.getPhone()), fields.capture(), eq(customer.getVersion()), eq(true));
    assertThat(result.appointmentStatus()).isEqualTo("待补充");
    assertThat(fields.getValue()).containsEntry("appointmentStatus", "待补充")
        .containsEntry("appointmentDate", "2026-09-01")
        .containsEntry("appointmentStore", "东城店");
  }

  @Test
  void recognizedAppointmentCreatesOneTaskPerProjectAndKeepsProjectList() {
    CustomerQueryService customers = mock(CustomerQueryService.class);
    ProfileWriter writer = mock(ProfileWriter.class);
    ArrivalHandoverService handover = mock(ArrivalHandoverService.class);
    Customer customer = customer();
    when(handover.createOrRefresh(any(), eq("2026-08-26"), eq("14:00"), eq("虎门店"), any()))
        .thenReturn(new ArrivalHandoverTaskDecision(3L, false));

    BookingConfirmResult result = new CustomerBookingService(customers, writer, handover)
        .confirmRecognized(customer, "张丹山", "2026-08-26", "14:00", "虎门店", List.of("孕按", "通乳"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
    verify(handover).createOrRefresh(eq(customer), eq("2026-08-26"), eq("14:00"), eq("虎门店"), eq("孕按"));
    verify(handover).createOrRefresh(eq(customer), eq("2026-08-26"), eq("14:00"), eq("虎门店"), eq("通乳"));
    verify(writer).write(eq(customer.getPhone()), fields.capture(), eq(null), eq(true), any());
    assertThat(result.appointmentStatus()).isEqualTo("待补充");
    assertThat(fields.getValue()).containsEntry("appointmentItem", "孕按、通乳")
        .containsEntry("customerName", "张丹山")
        .containsEntry("appointmentStatus", "待补充");
  }

  private Customer customer() {
    Customer customer = new Customer();
    customer.setId(41L);
    customer.setPhone("18810001014");
    customer.setVersion(5);
    return customer;
  }
}
