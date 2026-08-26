package com.privateflow.modules.arrival;

import java.util.Map;

public record ManualAppointmentRequest(
    Integer customerVersion,
    Map<String, String> values) {
  public ManualAppointmentRequest {
    values = values == null ? Map.of() : Map.copyOf(values);
  }
}
