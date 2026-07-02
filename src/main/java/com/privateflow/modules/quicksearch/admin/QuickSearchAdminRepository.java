package com.privateflow.modules.quicksearch.admin;

import com.privateflow.modules.quicksearch.ContentType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class QuickSearchAdminRepository {

  private static final String SHORTCUT_INDEX = "uk_shortcut_code";
  private static final String TYPE_INDEX = "idx_content_type";
  private static final String LEAD_INDEX = "idx_lead_type";
  private static final String ENABLED_INDEX = "idx_is_enabled";
  private final JdbcTemplate jdbcTemplate;

  public QuickSearchAdminRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<QuickSearchAdminItem> list() {
    return jdbcTemplate.query("""
        SELECT id, content_type, lead_type, title, shortcut_code, content, image_url,
               sort_order, is_enabled, created_by, created_at, updated_at
        FROM quick_search_items
        ORDER BY content_type ASC, sort_order ASC, shortcut_code ASC
        """, this::map);
  }

  public Optional<QuickSearchAdminItem> find(long id) {
    return jdbcTemplate.query("""
        SELECT id, content_type, lead_type, title, shortcut_code, content, image_url,
               sort_order, is_enabled, created_by, created_at, updated_at
        FROM quick_search_items
        WHERE id = ?
        LIMIT 1
        """, this::map, id).stream().findFirst();
  }

  public boolean shortcutExists(String shortcutCode, Long exceptId) {
    String except = exceptId == null ? "" : " AND id <> ?";
    Object[] args = exceptId == null
        ? new Object[] { shortcutCode }
        : new Object[] { shortcutCode, exceptId };
    Long count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM quick_search_items
        WHERE LOWER(shortcut_code) = LOWER(?)
        """ + except, Long.class, args);
    return count != null && count > 0;
  }

  public long create(QuickSearchItemRequest request, String operator) {
    jdbcTemplate.update("""
        INSERT INTO quick_search_items
          (content_type, scene, lead_type, title, shortcut_code, content, image_url, sort_order, is_enabled, created_by)
        VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        request.contentType().name(),
        normalizedLeadType(request.leadType()),
        request.title().trim(),
        request.shortcutCode().trim(),
        request.content().trim(),
        imageUrl(request),
        request.sortOrder() == null ? 0 : request.sortOrder(),
        Boolean.FALSE.equals(request.enabled()) ? 0 : 1,
        operator);
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0 : id;
  }

  public void update(long id, QuickSearchItemRequest request) {
    jdbcTemplate.update("""
        UPDATE quick_search_items
        SET lead_type = COALESCE(?, lead_type),
            title = COALESCE(?, title),
            shortcut_code = COALESCE(?, shortcut_code),
            content = COALESCE(?, content),
            image_url = COALESCE(?, image_url),
            sort_order = COALESCE(?, sort_order),
            is_enabled = COALESCE(?, is_enabled),
            updated_at = NOW()
        WHERE id = ?
        """,
        request.leadType() == null ? null : normalizedLeadType(request.leadType()),
        blankToNull(request.title()),
        blankToNull(request.shortcutCode()),
        blankToNull(request.content()),
        request.imageUrl(),
        request.sortOrder(),
        request.enabled() == null ? null : (request.enabled() ? 1 : 0),
        id);
  }

  public boolean toggle(long id) {
    jdbcTemplate.update("UPDATE quick_search_items SET is_enabled = IF(is_enabled = 1, 0, 1), updated_at = NOW() WHERE id = ?", id);
    return find(id).map(QuickSearchAdminItem::enabled).orElse(false);
  }

  public int delete(long id) {
    return jdbcTemplate.update("DELETE FROM quick_search_items WHERE id = ?", id);
  }

  public void enqueueCleanup(String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) {
      return;
    }
    jdbcTemplate.update("""
        INSERT INTO cos_cleanup_queue (image_url, deleted_at, status)
        VALUES (?, NOW(), 'PENDING')
        """, imageUrl);
  }

  private QuickSearchAdminItem map(ResultSet rs, int rowNum) throws SQLException {
    return new QuickSearchAdminItem(
        rs.getLong("id"),
        ContentType.valueOf(rs.getString("content_type")),
        rs.getString("lead_type"),
        rs.getString("title"),
        rs.getString("shortcut_code"),
        rs.getString("content"),
        rs.getString("image_url"),
        rs.getInt("sort_order"),
        rs.getInt("is_enabled") == 1,
        rs.getString("created_by"),
        rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime());
  }

  private String normalizedLeadType(String leadType) {
    return leadType == null || leadType.isBlank() ? "GENERAL" : leadType.trim().toUpperCase();
  }

  private String imageUrl(QuickSearchItemRequest request) {
    return request.contentType() == ContentType.IMAGE ? request.imageUrl() : null;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
