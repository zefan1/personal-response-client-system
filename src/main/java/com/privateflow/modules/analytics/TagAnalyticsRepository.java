package com.privateflow.modules.analytics;

import com.privateflow.modules.customer.admin.CustomerQuerySpec;
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

  private final JdbcTemplate jdbcTemplate;

  public TagAnalyticsRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public TagAnalyticsResponse analyze(
      CustomerQuerySpec customerSpec,
      CustomerQuerySpec optionSpec,
      TagAnalyticsWindow window) {
    return new TagAnalyticsResponse(
        loadSnapshotSummary(customerSpec),
        loadCategories(customerSpec),
        loadTags(customerSpec),
        loadStores(customerSpec),
        loadTeams(customerSpec),
        loadEmployees(customerSpec),
        List.of(),
        List.of(),
        List.of(),
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
}
