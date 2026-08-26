package com.privateflow.modules.arrival;

import java.util.Map;
import java.util.List;

public record ManualAppointmentForm(
    long customerId,
    int customerVersion,
    String nickname,
    String phone,
    Map<String, String> values,
    List<ManualAppointmentField> fields) {
}
