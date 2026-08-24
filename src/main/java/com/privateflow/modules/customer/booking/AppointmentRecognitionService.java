package com.privateflow.modules.customer.booking;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.common.events.RecognizedConversationEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Turns an explicit appointment block in a resolved chat into a booking task. */
@Component
public class AppointmentRecognitionService {

  private static final Logger log = LoggerFactory.getLogger(AppointmentRecognitionService.class);

  private final CustomerQueryService customers;
  private final CustomerBookingService bookings;
  private final AppointmentMessageParser parser;

  public AppointmentRecognitionService(
      CustomerQueryService customers,
      CustomerBookingService bookings,
      AppointmentMessageParser parser) {
    this.customers = customers;
    this.bookings = bookings;
    this.parser = parser;
  }

  @Async("profileUpdateExecutor")
  @EventListener
  public void onRecognizedConversation(RecognizedConversationEvent event) {
    if (event == null) return;
    handle(event.customerId(), event.phone(), event.rawMessages(), null);
  }

  @Async("profileUpdateExecutor")
  @EventListener
  public void onConfirmedEmployeeMessage(CustomerMessageSentEvent event) {
    if (event == null) return;
    handle(event.customerId(), event.phone(), event.rawMessages(), event.sentText());
  }

  void handle(Long customerId, String phone, List<CustomerMessageSentEvent.ChatMessage> rawMessages, String sentText) {
    Customer customer = resolve(customerId, phone);
    if (customer == null) return;
    Optional<AppointmentMessageParser.AppointmentDetails> parsed = parser.parse(rawMessages, sentText);
    if (parsed.isEmpty()) return;
    var appointment = parsed.orElseThrow();
    try {
      bookings.confirmRecognized(
          customer,
          appointment.personName(),
          appointment.date().toString(),
          appointment.time(),
          appointment.store(),
          appointment.projects());
      log.info("appointment recognized and saved, customerId={}, projects={}, date={}, time={}, store={}",
          customer.getId(), appointment.projects(), appointment.date(), appointment.time(), appointment.store());
    } catch (RuntimeException ex) {
      log.warn("appointment recognition save failed, customerId={}, reason={}", customer.getId(), ex.getMessage());
    }
  }

  private Customer resolve(Long customerId, String phone) {
    if (customerId != null && customerId > 0) {
      Customer customer = customers.getById(customerId);
      if (customer != null) return customer;
    }
    return phone == null || phone.isBlank() ? null : customers.getByPhone(phone);
  }
}
