package com.privateflow.modules.arrival;

import java.util.List;
import java.util.Map;

public record ManualAppointmentResult(
    boolean databaseSaved,
    boolean synced,
    long taskId,
    String wecomRowId,
    String syncError,
    int customerVersion,
    List<String> changedFields,
    String templateContent,
    Map<String, String> templateValues) {
  public ManualAppointmentResult {
    changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
    templateValues = templateValues == null ? Map.of() : Map.copyOf(templateValues);
  }
}
