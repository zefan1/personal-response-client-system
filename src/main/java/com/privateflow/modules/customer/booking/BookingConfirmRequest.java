package com.privateflow.modules.customer.booking;

public record BookingConfirmRequest(
    String appointmentDate,
    String appointmentTime,
    String appointmentStore,
    String appointmentItem) {
}
