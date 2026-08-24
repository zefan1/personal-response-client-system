package com.privateflow.modules.customer.booking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.RecognizedConversationEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AppointmentRecognitionServiceTest {

  @Test
  void recognizedConversationIsPassedToBookingWorkflow() {
    CustomerQueryService customers = mock(CustomerQueryService.class);
    CustomerBookingService bookings = mock(CustomerBookingService.class);
    AppointmentMessageParser parser = mock(AppointmentMessageParser.class);
    Customer customer = new Customer();
    customer.setId(41L);
    customer.setPhone("18810001014");
    when(customers.getById(41L)).thenReturn(customer);
    when(parser.parse(any(), eq(null))).thenReturn(Optional.of(
        new AppointmentMessageParser.AppointmentDetails(
            "张丹山", LocalDate.of(2026, 8, 26), "14:00", "虎门店", List.of("产康评估"))));

    new AppointmentRecognitionService(customers, bookings, parser)
        .onRecognizedConversation(new RecognizedConversationEvent(41L, customer.getPhone(), List.of(), "admin"));

    verify(bookings).confirmRecognized(customer, "张丹山", "2026-08-26", "14:00", "虎门店", List.of("产康评估"));
  }
}
