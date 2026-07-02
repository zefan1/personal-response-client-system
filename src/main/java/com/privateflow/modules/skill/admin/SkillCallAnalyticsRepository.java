package com.privateflow.modules.skill.admin;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SkillCallAnalyticsRepository {

  private final JdbcTemplate jdbcTemplate;

  public SkillCallAnalyticsRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public SkillCallAnalytics query(int days, String scene, String leadType) {
    List<Object> args = new ArrayList<>();
    String where = buildWhere(days, scene, leadType, args);
    SkillCallAnalytics.Summary summary = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) AS total_calls,
               COALESCE(SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), 0) AS success_count,
               COALESCE(AVG(response_time), 0) AS avg_response_time
        FROM skill_call_logs
        """ + where,
        (rs, rowNum) -> {
          long total = rs.getLong("total_calls");
          long success = rs.getLong("success_count");
          double successRate = ratio(success, total);
          double adoptionRate = 0.0;
          return new SkillCallAnalytics.Summary(
              total,
              successRate,
              Math.round(rs.getDouble("avg_response_time")),
              adoptionRate);
        },
        args.toArray());

    List<SkillCallAnalytics.Detail> details = jdbcTemplate.query("""
        SELECT scene, lead_type,
               COUNT(*) AS total_calls,
               COALESCE(SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), 0) AS success_count,
               COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0) AS fail_count,
               COALESCE(AVG(response_time), 0) AS avg_response_time
        FROM skill_call_logs
        """ + where + " GROUP BY scene, lead_type ORDER BY total_calls DESC, scene ASC, lead_type ASC",
        (rs, rowNum) -> new SkillCallAnalytics.Detail(
            rs.getString("scene"),
            rs.getString("lead_type"),
            rs.getLong("total_calls"),
            rs.getLong("success_count"),
            rs.getLong("fail_count"),
            Math.round(rs.getDouble("avg_response_time")),
            0.0),
        args.toArray());
    return new SkillCallAnalytics(summary == null ? new SkillCallAnalytics.Summary(0, 0.0, 0, 0.0) : summary, details);
  }

  private String buildWhere(int days, String scene, String leadType, List<Object> args) {
    StringBuilder where = new StringBuilder(" WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)");
    args.add(Math.max(1, Math.min(days, 90)));
    if (scene != null && !scene.isBlank()) {
      where.append(" AND scene = ?");
      args.add(scene.trim());
    }
    if (leadType != null && !leadType.isBlank()) {
      where.append(" AND lead_type = ?");
      args.add(leadType.trim());
    }
    return where.toString();
  }

  private static double ratio(long numerator, long denominator) {
    return denominator == 0 ? 0.0 : (double) numerator / (double) denominator;
  }
}
