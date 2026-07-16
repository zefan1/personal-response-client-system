package com.privateflow.modules.analytics;

import com.privateflow.modules.customer.admin.CustomerQuerySpec;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TagAnalyticsRepository {

  private static final String CURRENT_TAG_FROM = """
      FROM customers c
      JOIN customer_tag_assignments a ON a.customer_id = c.id AND a.is_active = 1
      JOIN tag_categories tc ON tc.id = a.category_id
        AND tc.is_enabled = 1 AND tc.merged_into_id IS NULL AND tc.use_for_statistics = 1
      JOIN tag_values tv ON tv.id = a.tag_value_id AND tv.category_id = a.category_id
        AND tv.is_enabled = 1 AND tv.merged_into_id IS NULL
      """;

  private static final String TAG_EVENT_FROM = """
      FROM customers c
      JOIN customer_tag_assignments a ON a.customer_id = c.id
      JOIN tag_categories tc ON tc.id = a.category_id
        AND tc.is_enabled = 1 AND tc.merged_into_id IS NULL AND tc.use_for_statistics = 1
      JOIN tag_values tv ON tv.id = a.tag_value_id AND tv.category_id = a.category_id
        AND tv.is_enabled = 1 AND tv.merged_into_id IS NULL
      """;

  private final JdbcTemplate jdbcTemplate;

  public TagAnalyticsRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public TagAnalyticsResponse analyze(
      CustomerQuerySpec customerSpec,
      CustomerQuerySpec optionSpec,
      TagAnalyticsWindow window) {
    TagAnalyticsResponse.Summary snapshot = loadSnapshotSummary(customerSpec);
    EventSummary events = loadEventSummary(customerSpec, window);
    return new TagAnalyticsResponse(
        new TagAnalyticsResponse.Summary(
            snapshot.matchedCustomerCount(),
            snapshot.taggedCustomerCount(),
            snapshot.activeAssignmentCount(),
            snapshot.coverageRate(),
            events.systemAddedCount(),
            events.manualAddedOrChangedCount(),
            events.systemDecidedNoUpdateCount()),
        loadCategories(customerSpec),
        loadTags(customerSpec),
        loadStores(customerSpec),
        loadTeams(customerSpec),
        loadEmployees(customerSpec),
        loadTagSources(customerSpec, window),
        loadUnupdatedReasons(customerSpec, window),
        loadTrend(customerSpec, window),
        loadFilterOptions(optionSpec),
        new TagAnalyticsResponse.AppliedWindow(window.from(), window.to(), window.granularity()));
  }

  public List<String> resolveEnabledKeeperPhones(List<Long> teamLeaderIds) {
    if (teamLeaderIds == null || teamLeaderIds.isEmpty()) {
      return List.of();
    }
    String placeholders = String.join(",", java.util.Collections.nCopies(teamLeaderIds.size(), "?"));
    return jdbcTemplate.queryForList("""
        SELECT COALESCE(phone, username)
        FROM accounts
        WHERE role = 'KEEPER' AND is_enabled = 1 AND leader_id IN (%s)
        ORDER BY id ASC
        """.formatted(placeholders), String.class, teamLeaderIds.toArray());
  }

  private TagAnalyticsResponse.Summary loadSnapshotSummary(CustomerQuerySpec spec) {
    Long matched = jdbcTemplate.queryForObject(
        "SELECT COUNT(DISTINCT c.id) FROM customers c " + spec.whereClause(),
        Long.class,
        spec.args().toArray());
    Map<String, Object> tagged = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) AS assignment_count, COUNT(DISTINCT c.id) AS customer_count
        """ + CURRENT_TAG_FROM + spec.whereClause(),
        (rs, rowNum) -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("assignments", rs.getLong("assignment_count"));
          row.put("customers", rs.getLong("customer_count"));
          return row;
        },
        spec.args().toArray());
    long matchedCount = matched == null ? 0L : matched;
    long taggedCount = tagged == null ? 0L : ((Number) tagged.get("customers")).longValue();
    long assignmentCount = tagged == null ? 0L : ((Number) tagged.get("assignments")).longValue();
    return new TagAnalyticsResponse.Summary(
        matchedCount,
        taggedCount,
        assignmentCount,
        matchedCount == 0 ? 0.0 : (double) taggedCount / matchedCount,
        0,
        0,
        0);
  }

  private EventSummary loadEventSummary(CustomerQuerySpec spec, TagAnalyticsWindow window) {
    Map<String, Long> counts = jdbcTemplate.queryForObject("""
        SELECT
          COALESCE(SUM(CASE WHEN a.source_type = 'SYSTEM_INFERENCE' THEN 1 ELSE 0 END), 0) AS system_count,
          COALESCE(SUM(CASE WHEN a.source_type = 'MANUAL' THEN 1 ELSE 0 END), 0) AS manual_count
        """ + TAG_EVENT_FROM + spec.whereClause() + " AND a.created_at >= ? AND a.created_at <= ?",
        (rs, rowNum) -> {
          Map<String, Long> row = new LinkedHashMap<>();
          row.put("system", rs.getLong("system_count"));
          row.put("manual", rs.getLong("manual_count"));
          return row;
        }, eventArgs(spec, window.from(), window.to()));
    long systemDecidedNoUpdateCount = jdbcTemplate.queryForObject("""
        SELECT COUNT(ar.id)
        FROM customers c
        JOIN tag_analysis_runs r ON r.customer_id = c.id
        JOIN tag_analysis_results ar ON ar.analysis_run_id = r.id
        """ + spec.whereClause() + """
          AND r.finished_at >= ? AND r.finished_at <= ?
          AND (r.status <> 'COMPLETED' OR ar.validation_status = 'REJECTED' OR ar.requested_action = 'NONE')
        """, Long.class, eventArgs(spec, window.from(), window.to()));
    return new EventSummary(
        counts.getOrDefault("system", 0L),
        counts.getOrDefault("manual", 0L),
        systemDecidedNoUpdateCount == 0 ? 0L : systemDecidedNoUpdateCount);
  }

  private List<TagAnalyticsResponse.ReasonRow> loadUnupdatedReasons(
      CustomerQuerySpec spec,
      TagAnalyticsWindow window) {
    long noAnalysis = countCustomers(spec,
        " AND NOT EXISTS (SELECT 1 FROM tag_analysis_runs r WHERE r.customer_id = c.id)");
    long rejected = countLatestRunCustomers(spec, window, "REJECTED");
    long noChange = countLatestRunCustomers(spec, window, "NO_CHANGE");
    long unmatched = countCustomers(spec, """
        AND EXISTS (
          SELECT 1 FROM unmatched_legacy_tag_values u
          WHERE u.customer_id = c.id AND u.status = 'PENDING'
        )
        """);
    long updatedAfterTagChange = countCustomers(spec, """
        AND EXISTS (SELECT 1 FROM customer_tag_assignments a0 WHERE a0.customer_id = c.id)
        AND c.updated_at > (
          SELECT MAX(COALESCE(a1.invalidated_at, a1.created_at))
          FROM customer_tag_assignments a1 WHERE a1.customer_id = c.id
        )
        """);
    return List.of(
        new TagAnalyticsResponse.ReasonRow(
            "NO_ANALYSIS", "未进行分析", "CURRENT_GAP", noAnalysis, 0, null),
        new TagAnalyticsResponse.ReasonRow(
            "LATEST_RUN_REJECTED", "最近一次分析被拒绝", "EVENT_WINDOW", rejected,
            countLatestRunDecisions(spec, window, "REJECTED"),
            sampleLatestReason(spec, window, "REJECTED")),
        new TagAnalyticsResponse.ReasonRow(
            "LATEST_RUN_NO_CHANGE", "最近一次分析无变化", "EVENT_WINDOW", noChange,
            countLatestRunDecisions(spec, window, "NO_CHANGE"),
            sampleLatestReason(spec, window, "NO_CHANGE")),
        new TagAnalyticsResponse.ReasonRow(
            "UNMATCHED_LEGACY_VALUE", "存在未匹配旧标签", "CURRENT_GAP", unmatched, 0, null),
        new TagAnalyticsResponse.ReasonRow(
            "CUSTOMER_UPDATED_AFTER_TAG_CHANGE", "标签变化后客户再次更新", "CURRENT_GAP",
            updatedAfterTagChange, 0, null));
  }

  private long countCustomers(CustomerQuerySpec spec, String condition) {
    Long count = jdbcTemplate.queryForObject(
        "SELECT COUNT(DISTINCT c.id) FROM customers c " + spec.whereClause() + condition,
        Long.class,
        spec.args().toArray());
    return count == null ? 0L : count;
  }

  private long countLatestRunCustomers(CustomerQuerySpec spec, TagAnalyticsWindow window, String status) {
    String condition = """
        AND EXISTS (
          SELECT 1 FROM tag_analysis_runs r
          WHERE r.id = (SELECT MAX(r2.id) FROM tag_analysis_runs r2 WHERE r2.customer_id = c.id)
            AND r.finished_at >= ? AND r.finished_at <= ? AND r.status = ?
        )
        """;
    List<Object> args = new ArrayList<>(spec.args());
    args.add(window.from());
    args.add(window.to());
    args.add(status);
    Long count = jdbcTemplate.queryForObject(
        "SELECT COUNT(DISTINCT c.id) FROM customers c " + spec.whereClause() + condition,
        Long.class,
        args.toArray());
    return count == null ? 0L : count;
  }

  private long countLatestRunDecisions(CustomerQuerySpec spec, TagAnalyticsWindow window, String status) {
    String query = """
        SELECT COUNT(ar.id)
        FROM customers c
        JOIN tag_analysis_runs r ON r.customer_id = c.id
        JOIN tag_analysis_results ar ON ar.analysis_run_id = r.id
        """ + spec.whereClause() + """
          AND r.id = (SELECT MAX(r2.id) FROM tag_analysis_runs r2 WHERE r2.customer_id = c.id)
          AND r.finished_at >= ? AND r.finished_at <= ? AND r.status = ?
        """;
    List<Object> args = new ArrayList<>(spec.args());
    args.add(window.from());
    args.add(window.to());
    args.add(status);
    Long count = jdbcTemplate.queryForObject(query, Long.class, args.toArray());
    return count == null ? 0L : count;
  }

  private String sampleLatestReason(CustomerQuerySpec spec, TagAnalyticsWindow window, String status) {
    String query = """
        SELECT COALESCE(NULLIF(TRIM(r.error_message), ''),
                        NULLIF(TRIM(ar.validation_reason), ''),
                        ar.result_type) AS sample_reason
        FROM customers c
        JOIN tag_analysis_runs r ON r.customer_id = c.id
        JOIN tag_analysis_results ar ON ar.analysis_run_id = r.id
        """ + spec.whereClause() + """
          AND r.id = (SELECT MAX(r2.id) FROM tag_analysis_runs r2 WHERE r2.customer_id = c.id)
          AND r.finished_at >= ? AND r.finished_at <= ? AND r.status = ?
        ORDER BY r.finished_at DESC, r.id DESC, ar.id ASC
        LIMIT 1
        """;
    List<Object> args = new ArrayList<>(spec.args());
    args.add(window.from());
    args.add(window.to());
    args.add(status);
    List<String> reasons = jdbcTemplate.queryForList(query, String.class, args.toArray());
    return reasons.isEmpty() ? null : reasons.get(0);
  }

  private List<TagAnalyticsResponse.SourceRow> loadTagSources(
      CustomerQuerySpec spec,
      TagAnalyticsWindow window) {
    return jdbcTemplate.query("""
        SELECT a.source_type,
               COUNT(*) AS added_assignment_count,
               COUNT(DISTINCT c.id) AS affected_customer_count
        """ + TAG_EVENT_FROM + spec.whereClause() + " AND a.created_at >= ? AND a.created_at <= ?" + """
        GROUP BY a.source_type
        ORDER BY added_assignment_count DESC, a.source_type ASC
        """, (rs, rowNum) -> new TagAnalyticsResponse.SourceRow(
            rs.getString("source_type"),
            sourceLabel(rs.getString("source_type")),
            rs.getLong("added_assignment_count"),
            rs.getLong("affected_customer_count")),
        eventArgs(spec, window.from(), window.to()));
  }

  private List<TagAnalyticsResponse.TrendRow> loadTrend(
      CustomerQuerySpec spec,
      TagAnalyticsWindow window) {
    Map<LocalDate, MutableTrend> values = new HashMap<>();
    jdbcTemplate.query("""
        SELECT DATE(a.created_at) AS event_day,
               COUNT(*) AS added_assignment_count,
               COALESCE(SUM(CASE WHEN a.source_type = 'SYSTEM_INFERENCE' THEN 1 ELSE 0 END), 0) AS system_count,
               COALESCE(SUM(CASE WHEN a.source_type = 'MANUAL' THEN 1 ELSE 0 END), 0) AS manual_count
        """ + TAG_EVENT_FROM + spec.whereClause() + " AND a.created_at >= ? AND a.created_at <= ?" + """
        GROUP BY DATE(a.created_at)
        """, rs -> {
          LocalDate date = rs.getDate("event_day").toLocalDate();
          MutableTrend item = values.computeIfAbsent(date, ignored -> new MutableTrend());
          item.addedAssignmentCount += rs.getLong("added_assignment_count");
          item.systemAddedCount += rs.getLong("system_count");
          item.manualAddedOrChangedCount += rs.getLong("manual_count");
        }, eventArgs(spec, window.from(), window.to()));
    jdbcTemplate.query("""
        SELECT DATE(a.invalidated_at) AS event_day,
               COUNT(*) AS invalidated_assignment_count
        """ + TAG_EVENT_FROM + spec.whereClause() + " AND a.invalidated_at >= ? AND a.invalidated_at <= ?" + """
        GROUP BY DATE(a.invalidated_at)
        """, rs -> {
          LocalDate date = rs.getDate("event_day").toLocalDate();
          MutableTrend item = values.computeIfAbsent(date, ignored -> new MutableTrend());
          item.invalidatedAssignmentCount += rs.getLong("invalidated_assignment_count");
        }, eventArgs(spec, window.from(), window.to()));

    List<TagAnalyticsResponse.TrendRow> rows = new ArrayList<>();
    for (LocalDate date = window.from().toLocalDate();
         !date.isAfter(window.to().toLocalDate());
         date = date.plusDays(1)) {
      MutableTrend item = values.getOrDefault(date, new MutableTrend());
      rows.add(new TagAnalyticsResponse.TrendRow(
          date,
          item.addedAssignmentCount,
          item.invalidatedAssignmentCount,
          item.addedAssignmentCount - item.invalidatedAssignmentCount,
          item.systemAddedCount,
          item.manualAddedOrChangedCount));
    }
    return rows;
  }

  private Object[] eventArgs(CustomerQuerySpec spec, java.time.LocalDateTime from, java.time.LocalDateTime to) {
    List<Object> args = new ArrayList<>(spec.args());
    args.add(from);
    args.add(to);
    return args.toArray();
  }

  private List<TagAnalyticsResponse.CategoryRow> loadCategories(CustomerQuerySpec spec) {
    return jdbcTemplate.query("""
        SELECT tc.id, tc.category_key, tc.category_name,
               COUNT(*) AS assignment_count, COUNT(DISTINCT c.id) AS customer_count
        """ + CURRENT_TAG_FROM + spec.whereClause() + """
        GROUP BY tc.id, tc.category_key, tc.category_name
        ORDER BY assignment_count DESC, tc.category_name ASC, tc.id ASC
        """, (rs, rowNum) -> new TagAnalyticsResponse.CategoryRow(
            rs.getLong("id"),
            rs.getString("category_key"),
            rs.getString("category_name"),
            rs.getLong("assignment_count"),
            rs.getLong("customer_count")),
        spec.args().toArray());
  }

  private List<TagAnalyticsResponse.TagRow> loadTags(CustomerQuerySpec spec) {
    return jdbcTemplate.query("""
        SELECT tc.id AS category_id, tc.category_key, tc.category_name,
               tv.id AS value_id, tv.tag_value, tv.display_name,
               COUNT(*) AS assignment_count, COUNT(DISTINCT c.id) AS customer_count
        """ + CURRENT_TAG_FROM + spec.whereClause() + """
        GROUP BY tc.id, tc.category_key, tc.category_name,
                 tv.id, tv.tag_value, tv.display_name
        ORDER BY assignment_count DESC, tc.category_name ASC, tv.display_name ASC, tv.id ASC
        """, (rs, rowNum) -> new TagAnalyticsResponse.TagRow(
            rs.getLong("category_id"),
            rs.getString("category_key"),
            rs.getString("category_name"),
            rs.getLong("value_id"),
            rs.getString("tag_value"),
            rs.getString("display_name"),
            rs.getLong("assignment_count"),
            rs.getLong("customer_count")),
        spec.args().toArray());
  }

  private List<TagAnalyticsResponse.DimensionRow> loadStores(CustomerQuerySpec spec) {
    return jdbcTemplate.query("""
        SELECT COALESCE(NULLIF(TRIM(c.intended_store), ''), 'UNASSIGNED_STORE') AS dimension_key,
               COUNT(*) AS assignment_count, COUNT(DISTINCT c.id) AS customer_count
        """ + CURRENT_TAG_FROM + spec.whereClause() + """
        GROUP BY COALESCE(NULLIF(TRIM(c.intended_store), ''), 'UNASSIGNED_STORE')
        ORDER BY assignment_count DESC, dimension_key ASC
        """, (rs, rowNum) -> {
          String key = rs.getString("dimension_key");
          return new TagAnalyticsResponse.DimensionRow(
              key,
              "UNASSIGNED_STORE".equals(key) ? "未填写门店" : key,
              rs.getLong("assignment_count"),
              rs.getLong("customer_count"));
        }, spec.args().toArray());
  }

  private List<TagAnalyticsResponse.DimensionRow> loadTeams(CustomerQuerySpec spec) {
    return jdbcTemplate.query("""
        SELECT leader.id AS leader_id,
               COALESCE(leader.display_name, '未归属团队') AS team_label,
               COUNT(*) AS assignment_count, COUNT(DISTINCT c.id) AS customer_count
        """ + CURRENT_TAG_FROM + """
        LEFT JOIN accounts employee
          ON COALESCE(employee.phone, employee.username) = c.assigned_keeper
         AND employee.is_enabled = 1
        LEFT JOIN accounts leader
          ON leader.id = employee.leader_id AND leader.is_enabled = 1
        """ + spec.whereClause() + """
        GROUP BY leader.id, COALESCE(leader.display_name, '未归属团队')
        ORDER BY assignment_count DESC, team_label ASC
        """, (rs, rowNum) -> {
          long leaderId = rs.getLong("leader_id");
          String key = rs.wasNull() ? "UNASSIGNED_TEAM" : String.valueOf(leaderId);
          return new TagAnalyticsResponse.DimensionRow(
              key,
              rs.getString("team_label"),
              rs.getLong("assignment_count"),
              rs.getLong("customer_count"));
        }, spec.args().toArray());
  }

  private List<TagAnalyticsResponse.DimensionRow> loadEmployees(CustomerQuerySpec spec) {
    return jdbcTemplate.query("""
        SELECT CASE WHEN employee.id IS NULL THEN 'UNASSIGNED_EMPLOYEE'
                    ELSE COALESCE(employee.phone, employee.username) END AS employee_key,
               CASE WHEN employee.id IS NULL THEN '未分配员工'
                    ELSE employee.display_name END AS employee_label,
               COUNT(*) AS assignment_count, COUNT(DISTINCT c.id) AS customer_count
        """ + CURRENT_TAG_FROM + """
        LEFT JOIN accounts employee
          ON COALESCE(employee.phone, employee.username) = c.assigned_keeper
         AND employee.is_enabled = 1
        """ + spec.whereClause() + """
        GROUP BY CASE WHEN employee.id IS NULL THEN 'UNASSIGNED_EMPLOYEE'
                      ELSE COALESCE(employee.phone, employee.username) END,
                 CASE WHEN employee.id IS NULL THEN '未分配员工'
                      ELSE employee.display_name END
        ORDER BY assignment_count DESC, employee_label ASC
        """, (rs, rowNum) -> new TagAnalyticsResponse.DimensionRow(
            rs.getString("employee_key"),
            rs.getString("employee_label"),
            rs.getLong("assignment_count"),
            rs.getLong("customer_count")), spec.args().toArray());
  }

  private TagAnalyticsResponse.FilterOptions loadFilterOptions(CustomerQuerySpec spec) {
    List<TagAnalyticsResponse.ValueOption> stores = jdbcTemplate.query("""
        SELECT DISTINCT TRIM(c.intended_store) AS option_value
        FROM customers c
        """ + spec.whereClause() + " AND c.intended_store IS NOT NULL AND TRIM(c.intended_store) <> '' ORDER BY option_value ASC",
        (rs, rowNum) -> new TagAnalyticsResponse.ValueOption(rs.getString("option_value"), rs.getString("option_value")),
        spec.args().toArray());
    List<TagAnalyticsResponse.ValueOption> sources = jdbcTemplate.query("""
        SELECT DISTINCT TRIM(c.source_channel) AS option_value
        FROM customers c
        """ + spec.whereClause() + " AND c.source_channel IS NOT NULL AND TRIM(c.source_channel) <> '' ORDER BY option_value ASC",
        (rs, rowNum) -> new TagAnalyticsResponse.ValueOption(rs.getString("option_value"), rs.getString("option_value")),
        spec.args().toArray());
    List<TagAnalyticsResponse.EmployeeOption> employees = jdbcTemplate.query("""
        SELECT DISTINCT COALESCE(employee.phone, employee.username) AS account,
                        employee.display_name, employee.leader_id
        FROM customers c
        JOIN accounts employee
          ON COALESCE(employee.phone, employee.username) = c.assigned_keeper
         AND employee.is_enabled = 1
        """ + spec.whereClause() + " ORDER BY employee.display_name ASC, account ASC",
        (rs, rowNum) -> new TagAnalyticsResponse.EmployeeOption(
            rs.getString("account"), rs.getString("display_name"), rs.getObject("leader_id", Long.class)),
        spec.args().toArray());
    List<TagAnalyticsResponse.TeamOption> teams = jdbcTemplate.query("""
        SELECT DISTINCT leader.id AS leader_id, leader.display_name
        FROM customers c
        JOIN accounts employee
          ON COALESCE(employee.phone, employee.username) = c.assigned_keeper
         AND employee.is_enabled = 1
        JOIN accounts leader
          ON leader.id = employee.leader_id AND leader.is_enabled = 1
        """ + spec.whereClause() + " ORDER BY leader.display_name ASC, leader.id ASC",
        (rs, rowNum) -> new TagAnalyticsResponse.TeamOption(
            rs.getLong("leader_id"), rs.getString("display_name")),
        spec.args().toArray());
    List<TagAnalyticsResponse.ValueOption> tagSources = jdbcTemplate.query("""
        SELECT DISTINCT a.source_type AS option_value
        FROM customers c
        JOIN customer_tag_assignments a ON a.customer_id = c.id
        JOIN tag_categories tc ON tc.id = a.category_id
          AND tc.is_enabled = 1 AND tc.merged_into_id IS NULL AND tc.use_for_statistics = 1
        JOIN tag_values tv ON tv.id = a.tag_value_id AND tv.category_id = a.category_id
          AND tv.is_enabled = 1 AND tv.merged_into_id IS NULL
        """ + spec.whereClause() + " ORDER BY option_value ASC",
        (rs, rowNum) -> new TagAnalyticsResponse.ValueOption(rs.getString("option_value"), sourceLabel(rs.getString("option_value"))),
        spec.args().toArray());
    return new TagAnalyticsResponse.FilterOptions(stores, teams, employees, sources, tagSources);
  }

  private String sourceLabel(String sourceType) {
    return switch (sourceType) {
      case "SYSTEM_INFERENCE" -> "系统推断";
      case "MANUAL" -> "人工设置";
      case "LEGACY_MIGRATION" -> "历史迁移";
      default -> sourceType == null || sourceType.isBlank() ? "未知来源" : sourceType;
    };
  }

  private record EventSummary(
      long systemAddedCount,
      long manualAddedOrChangedCount,
      long systemDecidedNoUpdateCount) {
  }

  private static final class MutableTrend {
    private long addedAssignmentCount;
    private long invalidatedAssignmentCount;
    private long systemAddedCount;
    private long manualAddedOrChangedCount;
  }
}
