package com.privateflow.modules.arrival;
import java.util.List;
public record ArrivalHandoverCompleteRequest(String visitType, String voucherRedeemed, String experienceProject, String projectType, String historicalExperienceCount, List<ArrivalReportAttachment> reports) {
  public ArrivalHandoverCompleteRequest {
    reports = reports == null ? List.of() : List.copyOf(reports);
  }
}
