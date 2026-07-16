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

  public List<String> resolveEnabledKeeperPhones(List<Long> teamLeaderIds) {
    return List.of();
  }

  public TagAnalyticsResponse analyze(
      CustomerQuerySpec customerSpec,
      CustomerQuerySpec optionSpec,
      TagAnalyticsWindow window) {
    return new TagAnalyticsResponse(
        loadSnapshotSummary(customerSpec),
        loadCategories(customerSpec),
        loadTags(customerSpec),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        TagAnalyticsResponse.FilterOptions.empty(),
        new TagAnalyticsResponse.AppliedWindow(window.from(), window.to(), window.granularity()));
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
}
