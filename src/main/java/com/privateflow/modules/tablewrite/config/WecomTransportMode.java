package com.privateflow.modules.tablewrite.config;

import java.util.Locale;

public enum WecomTransportMode {
  DIRECT,
  RELAY;

  public static WecomTransportMode from(String value) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      return DIRECT;
    }
    try {
      return valueOf(normalized.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("WECOM_TRANSPORT_MODE must be DIRECT or RELAY");
    }
  }
}
