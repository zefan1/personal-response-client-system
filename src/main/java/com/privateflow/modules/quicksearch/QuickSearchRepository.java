package com.privateflow.modules.quicksearch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class QuickSearchRepository {

  private final JdbcTemplate jdbcTemplate;

  public QuickSearchRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<QuickSearchItem> findEnabledItems() {
    return jdbcTemplate.query("""
        SELECT id, content_type, scene, lead_type, title, shortcut_code, content, image_url,
               sort_order, is_enabled, updated_at
        FROM quick_search_items
        WHERE is_enabled = 1
        ORDER BY sort_order ASC, shortcut_code ASC
        """, new QuickSearchItemMapper());
  }

  private static final class QuickSearchItemMapper implements RowMapper<QuickSearchItem> {
    @Override
    public QuickSearchItem mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new QuickSearchItem(
          rs.getLong("id"),
          ContentType.valueOf(rs.getString("content_type")),
          rs.getString("scene"),
          rs.getString("lead_type"),
          rs.getString("title"),
          rs.getString("shortcut_code"),
          rs.getString("content"),
          rs.getString("image_url"),
          rs.getInt("sort_order"),
          rs.getBoolean("is_enabled"),
          rs.getTimestamp("updated_at").toLocalDateTime());
    }
  }
}
