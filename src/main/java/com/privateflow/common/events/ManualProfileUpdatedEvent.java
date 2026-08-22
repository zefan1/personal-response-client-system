package com.privateflow.common.events;

import java.util.LinkedHashMap;
import java.util.Map;

/** A committed employee profile edit that must be projected to the configured Smart Sheets. */
public record ManualProfileUpdatedEvent(
    String phone,
    Map<String, Object> fields,
    String operator) {

  public ManualProfileUpdatedEvent {
    fields = fields == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(fields));
  }
}
