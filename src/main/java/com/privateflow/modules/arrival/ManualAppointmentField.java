package com.privateflow.modules.arrival;

import java.util.List;

/** A visible field from the configured arrival Smart Sheet mapping. */
public record ManualAppointmentField(
    String key,
    String label,
    String type,
    boolean editable,
    List<String> options) {
  public ManualAppointmentField {
    options = options == null ? List.of() : List.copyOf(options);
  }
}
