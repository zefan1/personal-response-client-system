package com.privateflow.modules.analytics;

import com.privateflow.modules.api.Role;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsRepository {

  private final JdbcTemplate jdbcTemplate;

  public AnalyticsRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Map<String, Object> overview(int days, String leadType, AnalyticsScope scope) {
    List<Object> skillArgs = new ArrayList<>();
    String skillWhere = skillWhere(days, leadType, scope, skillArgs);
    Map<String, Object> summary = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) AS total_calls,
               COALESCE(AVG(response_time), 0) AS avg_response_time,
               COUNT(DISTINCT caller) AS active_caller_count
        FROM skill_call_logs
        """ + skillWhere,
        (rs, rowNum) -> mapOf(
            "totalCalls", rs.getLong("total_calls"),
            "adoptionCount", 0L,
            "adoptionRate", 0.0,
            "avgResponseTimeMs", Math.round(rs.getDouble("avg_response_time")),
            "activeCallerCount", rs.getLong("active_caller_count")),
        skillArgs.toArray());

    long totalCalls = number(summary, "totalCalls");
    long adoptionCount = adoptionCount(days, scope);
    summary.put("adoptionCount", adoptionCount);
    summary.put("adoptionRate", ratio(adoptionCount, totalCalls));

    List<Map<String, Object>> dailyTrend = dailyTrend(days, leadType, scope);
    List<Map<String, Object>> sceneBreakdown = sceneBreakdown(days, leadType, scope);
    long regenerates = countRegenerates(days, leadType, scope);
    return mapOf(
        "summary", summary,
        "dailyTrend", dailyTrend,
        "sceneBreakdown", sceneBreakdown,
        "regenerateStats", mapOf(
            "totalRegenerates", regenerates,
            "regenerateRate", ratio(regenerates, totalCalls),
            "regenerateDistribution", regenerateDistribution(days, leadType, scope)));
  }

  public Map<String, Object> funnels(String leadType, AnalyticsScope scope) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (leadType == null || "TUAN_GOU".equals(leadType)) {
      result.put("tuanGou", funnel("TUAN_GOU", scope, List.of(
          new Step("assignedCustomers", "assigned customers", ""),
          new Step("firstContact", "first contact", " AND followup_notes IS NOT NULL AND followup_notes <> ''"),
          new Step("appointmentConfirmed", "appointment confirmed", " AND followup_notes IS NOT NULL AND followup_notes <> '' AND customer_stage LIKE '%\u9884\u7ea6%'"),
          new Step("arrived", "arrived", " AND followup_notes IS NOT NULL AND followup_notes <> '' AND (arrived = '\u662f' OR customer_stage LIKE '%\u5df2\u5b8c\u6210%' OR customer_stage LIKE '%\u5230\u5e97%')"))));
    }
    if (leadType == null || "XIAN_SUO".equals(leadType)) {
      result.put("xianSuo", funnel("XIAN_SUO", scope, List.of(
          new Step("assignedCustomers", "assigned customers", ""),
          new Step("firstContact", "first contact", " AND followup_notes IS NOT NULL AND followup_notes <> ''"),
          new Step("intentConfirmed", "intent confirmed", " AND followup_notes IS NOT NULL AND followup_notes <> '' AND intent_level IN ('HIGH', 'MEDIUM')"),
          new Step("purchased", "purchased", " AND followup_notes IS NOT NULL AND followup_notes <> '' AND intent_level IN ('HIGH', 'MEDIUM') AND purchased_project IS NOT NULL AND purchased_project <> ''"),
          new Step("arrived", "arrived", " AND followup_notes IS NOT NULL AND followup_notes <> '' AND intent_level IN ('HIGH', 'MEDIUM') AND purchased_project IS NOT NULL AND purchased_project <> '' AND (arrived = '\u662f' OR customer_stage LIKE '%\u5df2\u5b8c\u6210%' OR customer_stage LIKE '%\u5230\u5e97%')"))));
    }
    return result;
  }

  public Map<String, Object> staff(int days, String leadType, AnalyticsScope scope) {
    List<Object> args = new ArrayList<>();
    String customerWhere = customerWhere(null, scope, args);
    List<Map<String, Object>> rows = jdbcTemplate.query("""
        SELECT COALESCE(c.assigned_keeper, '') AS caller,
               COUNT(*) AS total_customers,
               SUM(CASE WHEN c.next_followup_at IS NOT NULL AND c.next_followup_at < NOW() THEN 1 ELSE 0 END) AS overdue_count,
               SUM(CASE WHEN c.last_followup_at IS NULL OR c.last_followup_at < DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 1 ELSE 0 END) AS silent_count
        FROM customers c
        """ + customerWhere + leadFilter("c.lead_type", leadType, args) + """
        GROUP BY COALESCE(c.assigned_keeper, '')
        ORDER BY total_customers DESC, caller ASC
        """, (rs, rowNum) -> mapOf(
            "caller", rs.getString("caller"),
            "totalCustomers", rs.getLong("total_customers"),
            "totalCalls", skillCallsForCaller(days, leadType, rs.getString("caller")),
            "adoptionCount", adoptionsForCaller(days, rs.getString("caller")),
            "overdueCount", rs.getLong("overdue_count"),
            "silentCount", rs.getLong("silent_count")), args.toArray());
    for (Map<String, Object> row : rows) {
      row.put("adoptionRate", ratio(number(row, "adoptionCount"), number(row, "totalCalls")));
    }
    return mapOf("list", rows);
  }

  public Map<String, Object> sources(int days, String leadType, AnalyticsScope scope) {
    List<Object> args = new ArrayList<>();
    String where = customerWhere(days, scope, args) + leadFilter("c.lead_type", leadType, args);
    List<Map<String, Object>> list = jdbcTemplate.query("""
        SELECT COALESCE(c.source_channel, 'UNKNOWN') AS source_channel,
               COUNT(*) AS total,
               SUM(CASE WHEN c.lead_type = 'TUAN_GOU' THEN 1 ELSE 0 END) AS tuan_gou_count,
               SUM(CASE WHEN c.lead_type = 'XIAN_SUO' THEN 1 ELSE 0 END) AS xian_suo_count,
               SUM(CASE WHEN c.arrived = '\u662f' OR c.customer_stage LIKE '%\u5230\u5e97%' OR c.customer_stage LIKE '%\u5df2\u5b8c\u6210%' THEN 1 ELSE 0 END) AS arrived_count
        FROM customers c
        """ + where + """
        GROUP BY COALESCE(c.source_channel, 'UNKNOWN')
        ORDER BY total DESC, source_channel ASC
        """, (rs, rowNum) -> {
      long total = rs.getLong("total");
      long arrived = rs.getLong("arrived_count");
      return mapOf(
          "sourceChannel", rs.getString("source_channel"),
          "total", total,
          "tuanGouCount", rs.getLong("tuan_gou_count"),
          "xianSuoCount", rs.getLong("xian_suo_count"),
          "arrivedCount", arrived,
          "arrivalRate", ratio(arrived, total));
    }, args.toArray());
    return mapOf("list", list);
  }

  public Map<String, Object> stages(String leadType, AnalyticsScope scope) {
    List<Object> args = new ArrayList<>();
    String where = customerWhere(null, scope, args) + leadFilter("c.lead_type", leadType, args);
    List<Map<String, Object>> list = jdbcTemplate.query("""
        SELECT COALESCE(c.customer_stage, 'UNKNOWN') AS customer_stage,
               COUNT(*) AS total,
               SUM(CASE WHEN c.lead_type = 'TUAN_GOU' THEN 1 ELSE 0 END) AS tuan_gou_count,
               SUM(CASE WHEN c.lead_type = 'XIAN_SUO' THEN 1 ELSE 0 END) AS xian_suo_count
        FROM customers c
        """ + where + """
        GROUP BY COALESCE(c.customer_stage, 'UNKNOWN')
        ORDER BY total DESC, customer_stage ASC
        """, (rs, rowNum) -> mapOf(
            "customerStage", rs.getString("customer_stage"),
            "total", rs.getLong("total"),
            "tuanGouCount", rs.getLong("tuan_gou_count"),
            "xianSuoCount", rs.getLong("xian_suo_count")), args.toArray());
    return mapOf("list", list);
  }

  public Map<String, Object> health(int days, String leadType, AnalyticsScope scope) {
    List<Object> args = new ArrayList<>();
    String where = customerWhere(null, scope, args) + leadFilter("c.lead_type", leadType, args);
    Map<String, Object> summary = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) AS total_customers,
               COUNT(DISTINCT c.assigned_keeper) AS keeper_count,
               SUM(CASE WHEN c.next_followup_at IS NOT NULL AND c.next_followup_at < NOW() THEN 1 ELSE 0 END) AS overdue_count,
               SUM(CASE WHEN c.last_followup_at IS NULL OR c.last_followup_at < DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 1 ELSE 0 END) AS silent_count
        FROM customers c
        """ + where,
        (rs, rowNum) -> {
          long total = rs.getLong("total_customers");
          long keepers = rs.getLong("keeper_count");
          long overdue = rs.getLong("overdue_count");
          long silent = rs.getLong("silent_count");
          return mapOf(
              "totalCustomers", total,
              "keeperCount", keepers,
              "avgCustomersPerKeeper", keepers == 0 ? 0.0 : (double) total / (double) keepers,
              "overdueCount", overdue,
              "overdueRate", ratio(overdue, total),
              "silentCount", silent,
              "silentRate", ratio(silent, total));
        }, args.toArray());
    return mapOf("summary", summary, "systemAlerts", activeRiskAlerts(days));
  }

  public Map<String, Object> lifecycle(String leadType, AnalyticsScope scope) {
    List<Object> args = new ArrayList<>();
    String where = customerWhere(null, scope, args) + leadFilter("c.lead_type", leadType, args);
    List<Map<String, Object>> list = jdbcTemplate.query("""
        SELECT COALESCE(c.lead_type, 'PENDING') AS lead_type,
               COALESCE(AVG(CASE WHEN c.last_followup_at IS NOT NULL THEN TIMESTAMPDIFF(DAY, c.created_at, c.last_followup_at) END), 0) AS allocation_to_first_contact,
               COALESCE(AVG(CASE WHEN c.updated_at IS NOT NULL AND (c.arrived = '\u662f' OR c.customer_stage LIKE '%\u5230\u5e97%' OR c.customer_stage LIKE '%\u5df2\u5b8c\u6210%') THEN TIMESTAMPDIFF(DAY, c.created_at, c.updated_at) END), 0) AS allocation_to_arrival
        FROM customers c
        """ + where + """
        GROUP BY COALESCE(c.lead_type, 'PENDING')
        ORDER BY lead_type ASC
        """, (rs, rowNum) -> mapOf(
            "leadType", rs.getString("lead_type"),
            "allocationToFirstContact", round1(rs.getDouble("allocation_to_first_contact")),
            "allocationToArrival", round1(rs.getDouble("allocation_to_arrival")),
            "estimateSource", "customers.updated_at"), args.toArray());
    return mapOf("list", list);
  }

  public Map<String, Object> risks(int days, String leadType, AnalyticsScope scope) {
    List<Object> args = new ArrayList<>();
    String where = customerWhere(null, scope, args) + leadFilter("c.lead_type", leadType, args);
    List<Map<String, Object>> customers = jdbcTemplate.query("""
        SELECT c.phone, c.nickname, c.lead_type, c.customer_stage, c.assigned_keeper, c.last_followup_at, c.next_followup_at
        FROM customers c
        """ + where + """
          AND (c.next_followup_at < NOW() OR c.last_followup_at IS NULL OR c.last_followup_at < DATE_SUB(NOW(), INTERVAL 7 DAY))
        ORDER BY c.next_followup_at ASC, c.last_followup_at ASC
        LIMIT 100
        """, (rs, rowNum) -> mapOf(
            "phone", rs.getString("phone"),
            "nickname", rs.getString("nickname"),
            "leadType", rs.getString("lead_type"),
            "customerStage", rs.getString("customer_stage"),
            "assignedKeeper", rs.getString("assigned_keeper"),
            "lastFollowupAt", stringValue(rs.getTimestamp("last_followup_at")),
            "nextFollowupAt", stringValue(rs.getTimestamp("next_followup_at"))), args.toArray());
    return mapOf("customers", customers, "alerts", activeRiskAlerts(days));
  }

  public Map<String, Object> contentRanking(int days, String leadType, AnalyticsScope scope) {
    List<Object> args = new ArrayList<>();
    String where = auditWhere(days, scope, args) + " AND action IN ('COPY_REPLY', 'BATCH_TEMPLATE') ";
    List<Map<String, Object>> list = jdbcTemplate.query("""
        SELECT action, COALESCE(target_type, '') AS target_type, COALESCE(target_id, '') AS target_id,
               COUNT(*) AS use_count, MAX(detail) AS sample_detail
        FROM audit_logs
        """ + where + """
        GROUP BY action, COALESCE(target_type, ''), COALESCE(target_id, '')
        ORDER BY use_count DESC, action ASC
        LIMIT 50
        """, (rs, rowNum) -> mapOf(
            "action", rs.getString("action"),
            "targetType", rs.getString("target_type"),
            "targetId", rs.getString("target_id"),
            "useCount", rs.getLong("use_count"),
            "sampleDetail", rs.getString("sample_detail")), args.toArray());
    return mapOf("list", list, "leadTypeFilterApplied", leadType == null ? "ALL" : leadType);
  }

  private List<Map<String, Object>> dailyTrend(int days, String leadType, AnalyticsScope scope) {
    List<Object> args = new ArrayList<>();
    String where = skillWhere(days, leadType, scope, args);
    List<Map<String, Object>> rows = jdbcTemplate.query("""
        SELECT DATE(created_at) AS call_date,
               COUNT(*) AS total_calls,
               COUNT(DISTINCT caller) AS active_caller_count,
               COALESCE(AVG(response_time), 0) AS avg_response_time
        FROM skill_call_logs
        """ + where + """
        GROUP BY DATE(created_at)
        ORDER BY call_date ASC
        """, (rs, rowNum) -> {
      long total = rs.getLong("total_calls");
      long adoption = adoptionCountByDate(rs.getString("call_date"), scope);
      return mapOf(
          "date", rs.getString("call_date"),
          "totalCalls", total,
          "adoptionCount", adoption,
          "adoptionRate", ratio(adoption, total),
          "avgResponseTimeMs", Math.round(rs.getDouble("avg_response_time")),
          "activeCallerCount", rs.getLong("active_caller_count"));
    }, args.toArray());
    return rows;
  }

  private List<Map<String, Object>> sceneBreakdown(int days, String leadType, AnalyticsScope scope) {
    List<Object> args = new ArrayList<>();
    String where = skillWhere(days, leadType, scope, args);
    return jdbcTemplate.query("""
        SELECT scene,
               COUNT(*) AS total_calls,
               COALESCE(AVG(response_time), 0) AS avg_response_time
        FROM skill_call_logs
        """ + where + """
        GROUP BY scene
        ORDER BY total_calls DESC, scene ASC
        """, (rs, rowNum) -> {
      long total = rs.getLong("total_calls");
      long adoption = adoptionCount(days, scope);
      return mapOf(
          "scene", rs.getString("scene"),
          "totalCalls", total,
          "adoptionCount", adoption,
          "adoptionRate", ratio(adoption, total),
          "avgResponseTimeMs", Math.round(rs.getDouble("avg_response_time")));
    }, args.toArray());
  }

  private Map<String, Object> regenerateDistribution(int days, String leadType, AnalyticsScope scope) {
    List<Object> args = new ArrayList<>();
    String where = skillWhere(days, leadType, scope, args) + " AND scene = 'REGENERATE' ";
    Map<String, Object> row = jdbcTemplate.queryForObject("""
        SELECT SUM(CASE WHEN cnt = 1 THEN 1 ELSE 0 END) AS once_count,
               SUM(CASE WHEN cnt = 2 THEN 1 ELSE 0 END) AS twice_count,
               SUM(CASE WHEN cnt >= 3 THEN 1 ELSE 0 END) AS three_or_more_count
        FROM (
          SELECT caller, DATE(created_at) AS call_date, COUNT(*) AS cnt
          FROM skill_call_logs
        """ + where + """
          GROUP BY caller, DATE(created_at)
        ) x
        """, (rs, rowNum) -> mapOf(
            "once", rs.getLong("once_count"),
            "twice", rs.getLong("twice_count"),
            "threeOrMore", rs.getLong("three_or_more_count")), args.toArray());
    return row == null ? mapOf("once", 0L, "twice", 0L, "threeOrMore", 0L) : row;
  }

  private long countRegenerates(int days, String leadType, AnalyticsScope scope) {
    List<Object> args = new ArrayList<>();
    Long value = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM skill_call_logs " + skillWhere(days, leadType, scope, args) + " AND scene = 'REGENERATE'",
        Long.class,
        args.toArray());
    return value == null ? 0L : value;
  }

  private Map<String, Object> funnel(String leadType, AnalyticsScope scope, List<Step> steps) {
    List<Map<String, Object>> rows = new ArrayList<>();
    long first = 0L;
    long previous = 0L;
    for (Step step : steps) {
      List<Object> args = new ArrayList<>();
      String where = customerWhere(null, scope, args) + " AND c.lead_type = ? " + step.extraWhere();
      args.add(leadType);
      Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customers c " + where, Long.class, args.toArray());
      long count = value == null ? 0L : value;
      if (rows.isEmpty()) {
        first = count;
      }
      rows.add(mapOf(
          "stage", step.label(),
          "stageKey", step.key(),
          "count", count,
          "layerRate", rows.isEmpty() ? null : ratio(count, previous),
          "totalRate", rows.isEmpty() ? null : ratio(count, first)));
      previous = count;
    }
    return mapOf("stages", rows, "lifecycleAvgDays", lifecycle(leadType, scope).get("list"));
  }

  private List<Map<String, Object>> activeRiskAlerts(int days) {
    return jdbcTemplate.query("""
        SELECT alert_type, level, status, message, occurred_at, resolved_at
        FROM system_alerts
        WHERE alert_type IN ('CUSTOMER_COMPLAINT', 'CHURN_RISK')
          AND occurred_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
        ORDER BY occurred_at DESC
        LIMIT 50
        """, (rs, rowNum) -> mapOf(
            "alertType", rs.getString("alert_type"),
            "level", rs.getString("level"),
            "status", rs.getString("status"),
            "message", rs.getString("message"),
            "occurredAt", stringValue(rs.getTimestamp("occurred_at")),
            "resolvedAt", stringValue(rs.getTimestamp("resolved_at"))), days);
  }

  private long skillCallsForCaller(int days, String leadType, String caller) {
    List<Object> args = new ArrayList<>();
    String where = " WHERE success = 1 AND created_at >= DATE_SUB(NOW(), INTERVAL ? DAY) AND caller = ? ";
    args.add(days);
    args.add(caller);
    where += leadFilter("lead_type", leadType, args);
    Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM skill_call_logs " + where, Long.class, args.toArray());
    return value == null ? 0L : value;
  }

  private long adoptionsForCaller(int days, String caller) {
    Long value = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM audit_logs
        WHERE action = 'COPY_REPLY'
          AND created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
          AND operator = ?
        """, Long.class, days, caller);
    return value == null ? 0L : value;
  }

  private long adoptionCount(int days, AnalyticsScope scope) {
    List<Object> args = new ArrayList<>();
    Long value = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM audit_logs " + auditWhere(days, scope, args) + " AND action = 'COPY_REPLY'",
        Long.class,
        args.toArray());
    return value == null ? 0L : value;
  }

  private long adoptionCountByDate(String date, AnalyticsScope scope) {
    List<Object> args = new ArrayList<>();
    String where = " WHERE DATE(created_at) = ? ";
    args.add(date);
    where += operatorScope("operator", scope, args);
    Long value = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM audit_logs " + where + " AND action = 'COPY_REPLY'",
        Long.class,
        args.toArray());
    return value == null ? 0L : value;
  }

  private String skillWhere(int days, String leadType, AnalyticsScope scope, List<Object> args) {
    String where = " WHERE success = 1 AND created_at >= DATE_SUB(NOW(), INTERVAL ? DAY) ";
    args.add(days);
    where += leadFilter("lead_type", leadType, args);
    where += operatorScope("caller", scope, args);
    return where;
  }

  private String auditWhere(int days, AnalyticsScope scope, List<Object> args) {
    String where = " WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? DAY) ";
    args.add(days);
    where += operatorScope("operator", scope, args);
    return where;
  }

  private String customerWhere(Integer days, AnalyticsScope scope, List<Object> args) {
    String where = " WHERE 1=1 ";
    if (days != null) {
      where += " AND c.created_at >= DATE_SUB(NOW(), INTERVAL ? DAY) ";
      args.add(days);
    }
    where += operatorScope("c.assigned_keeper", scope, args);
    return where;
  }

  private String leadFilter(String column, String leadType, List<Object> args) {
    if (leadType == null) {
      return "";
    }
    args.add(leadType);
    return " AND " + column + " = ? ";
  }

  private String operatorScope(String column, AnalyticsScope scope, List<Object> args) {
    if (scope.role() == Role.ADMIN) {
      if (scope.caller() == null || scope.caller().isBlank()) {
        return "";
      }
      args.add(scope.caller());
      return " AND " + column + " = ? ";
    }
    if (scope.role() == Role.KEEPER) {
      args.add(scope.username());
      return " AND " + column + " = ? ";
    }
    String teamScope = " AND " + column + " IN ("
        + "SELECT COALESCE(a.phone, a.username) FROM accounts a "
        + "WHERE a.is_enabled = 1 AND (a.leader_id = (SELECT l.id FROM accounts l WHERE l.phone = ? OR l.username = ? LIMIT 1) "
        + "OR COALESCE(a.phone, a.username) = ?)) ";
    args.add(scope.username());
    args.add(scope.username());
    args.add(scope.username());
    if (scope.caller() != null && !scope.caller().isBlank()) {
      teamScope += " AND " + column + " = ? ";
      args.add(scope.caller());
    }
    return teamScope;
  }

  private Map<String, Object> mapOf(Object... values) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < values.length; i += 2) {
      map.put(String.valueOf(values[i]), values[i + 1]);
    }
    return map;
  }

  private long number(Map<String, Object> map, String key) {
    Object value = map.get(key);
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private double ratio(long numerator, long denominator) {
    return denominator == 0 ? 0.0 : (double) numerator / (double) denominator;
  }

  private double round1(double value) {
    return Math.round(value * 10.0) / 10.0;
  }

  private String stringValue(Object value) {
    return value == null ? null : value.toString();
  }

  private record Step(String key, String label, String extraWhere) {
  }
}
