package com.privateflow.modules.arrival;

import java.util.List;

public record ManualAppointmentReportRequest(List<ArrivalReportAttachment> reports) {
  public ManualAppointmentReportRequest {
    reports = reports == null ? List.of() : List.copyOf(reports);
  }
}
