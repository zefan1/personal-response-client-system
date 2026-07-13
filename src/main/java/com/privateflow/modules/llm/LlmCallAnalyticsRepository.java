package com.privateflow.modules.llm;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LlmCallAnalyticsRepository {

  private final JdbcTemplate jdbcTemplate;

  public LlmCallAnalyticsRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public LlmCallAnalytics query(int days, String scene, String leadType) {
    List<Object> args = new ArrayList<>();
    String where = buildWhere(days, scene, leadType, args);
    LlmCallAnalytics.Summary summary = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) AS total_calls,
               COALESCE(SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), 0) AS success_count,
               COALESCE(AVG(response_time), 0) AS avg_response_time
        FROM llm_call_logs
        """ + where,
        (rs, rowNum) -> {
          long total = rs.getLong("total_calls");
          long success = rs.getLong("success_count");
          return new LlmCallAnalytics.Summary(total, ratio(success, total), Math.round(rs.getDouble("avg_response_time")));
        },
        args.toArray());

    List<LlmCallAnalytics.Detail> details = jdbcTemplate.query("""
        SELECT scene, lead_type, llm_environment_id, model,
               COUNT(*) AS total_calls,
               COALESCE(SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), 0) AS success_count,
               COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0) AS fail_count,
               COALESCE(AVG(response_time), 0) AS avg_response_time
        FROM llm_call_logs
        """ + where + " GROUP BY scene, lead_type, llm_environment_id, model ORDER BY total_calls DESC, scene ASC, lead_type ASC",
        (rs, rowNum) -> new LlmCallAnalytics.Detail(
            rs.getString("scene"),
            rs.getString("lead_type"),
            rs.getObject("llm_environment_id") == null ? null : rs.getLong("llm_environment_id"),
            rs.getString("model"),
            rs.getLong("total_calls"),
            rs.getLong("success_count"),
            rs.getLong("fail_count"),
            Math.round(rs.getDouble("avg_response_time"))),
        args.toArray());
    return new LlmCallAnalytics(summary == null ? new LlmCallAnalytics.Summary(0, 0.0, 0) : summary, details);
  }

  private String buildWhere(int days, String scene, String leadType, List<Object> args) {
    StringBuilder where = new StringBuilder(" WHERE created_at >= ?");
    args.add(LocalDateTime.now().minusDays(Math.max(1, Math.min(days, 90))));
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
