package com.privateflow.common.events;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

/** A committed employee profile edit that must be projected to the configured Smart Sheets. */
public record ManualProfileUpdatedEvent(
    String phone,
    Map<String, Object> fields,
    String operator) {

  public ManualProfileUpdatedEvent {
    // Null values represent an intentional field clear. Keep them in the
    // event; table projection filters nulls before writing to WeCom.
    fields = fields == null
        ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
  }
}
