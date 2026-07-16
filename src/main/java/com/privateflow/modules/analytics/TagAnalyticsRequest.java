package com.privateflow.modules.analytics;

import com.privateflow.modules.customer.admin.CustomerSearchRequest;
import java.time.LocalDateTime;
import java.util.List;

public record TagAnalyticsRequest(
    CustomerSearchRequest customerFilter,
    List<Long> teamLeaderIds,
    LocalDateTime tagFrom,
    LocalDateTime tagTo,
    TagTrendGranularity granularity) {

  public TagAnalyticsRequest {
    teamLeaderIds = teamLeaderIds == null ? List.of() : List.copyOf(teamLeaderIds);
  }
}
