package com.privateflow.modules.arrival;

import java.util.List;

public record ManualAppointmentResult(
    boolean databaseSaved,
    boolean synced,
    long taskId,
    String wecomRowId,
    String syncError,
    int customerVersion,
    List<String> changedFields,
    String templateContent) {
  public ManualAppointmentResult {
    changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
  }
}
