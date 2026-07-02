package com.privateflow.modules.skill.admin;

import java.util.List;

public record SkillCallAnalytics(
    Summary summary,
    List<Detail> details
) {

  public record Summary(
      long totalCalls,
      double successRate,
      long avgResponseTimeMs,
      double adoptionRate
  ) {
  }

  public record Detail(
      String scene,
      String leadType,
      long totalCalls,
      long successCount,
      long failCount,
      long avgResponseTimeMs,
      double adoptionRate
  ) {
  }
}
