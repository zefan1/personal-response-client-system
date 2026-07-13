package com.privateflow.modules.llm;

import java.util.List;

public record LlmCallAnalytics(
    Summary summary,
    List<Detail> details
) {
  public record Summary(long totalCalls, double successRate, long avgResponseTime) {
  }

  public record Detail(
      String scene,
      String leadType,
      Long environmentId,
      String model,
      long totalCalls,
      long successCount,
      long failCount,
      long avgResponseTime
  ) {
  }
}
