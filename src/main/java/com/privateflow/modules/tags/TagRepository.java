package com.privateflow.modules.tags;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TagRepository {

  private final JdbcTemplate jdbcTemplate;

  public TagRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<TagCategory> listTree() {
    List<TagCategory> categories = jdbcTemplate.query("""
        SELECT id, category_key, category_name, bound_field, is_builtin, is_enabled, sort_order, created_at, updated_at
        FROM tag_categories
        ORDER BY sort_order ASC, id ASC
        """, (rs, rowNum) -> mapCategory(rs, values(rs.getLong("id"))));
    return categories;
  }

  public Optional<TagCategory> findCategory(long id) {
    return jdbcTemplate.query("""
        SELECT id, category_key, category_name, bound_field, is_builtin, is_enabled, sort_order, created_at, updated_at
        FROM tag_categories WHERE id = ? LIMIT 1
        """, (rs, rowNum) -> mapCategory(rs, values(id)), id).stream().findFirst();
  }

  public Optional<TagValue> findValue(long id) {
    return jdbcTemplate.query("""
        SELECT v.id, v.category_id, c.category_key, v.tag_value, v.display_name, v.is_enabled, v.sort_order, v.created_at, v.updated_at
        FROM tag_values v JOIN tag_categories c ON c.id = v.category_id
        WHERE v.id = ? LIMIT 1
        """, this::mapValue, id).stream().findFirst();
  }

  public boolean boundFieldExists(String boundField) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tag_categories WHERE bound_field = ?", Integer.class, boundField);
    return count != null && count > 0;
  }

  public boolean categoryKeyExists(String categoryKey) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tag_categories WHERE category_key = ?", Integer.class, categoryKey);
    return count != null && count > 0;
  }

  public boolean valueExists(long categoryId, String tagValue) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tag_values WHERE category_id = ? AND tag_value = ?",
        Integer.class,
        categoryId,
        tagValue);
    return count != null && count > 0;
  }

  public int valueCount(long categoryId) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tag_values WHERE category_id = ?", Integer.class, categoryId);
    return count == null ? 0 : count;
  }

  public long createCategory(String categoryKey, TagCategoryRequest request, int sortOrder) {
    jdbcTemplate.update("""
        INSERT INTO tag_categories (category_key, category_name, bound_field, is_builtin, is_enabled, sort_order)
        VALUES (?, ?, ?, 0, 1, ?)
        """, categoryKey, request.categoryName().trim(), request.boundField().trim(), sortOrder);
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0L : id;
  }

  public void updateCategory(long id, TagCategoryRequest request) {
    jdbcTemplate.update("""
        UPDATE tag_categories
        SET category_name = COALESCE(?, category_name),
            is_enabled = COALESCE(?, is_enabled),
            sort_order = COALESCE(?, sort_order),
            updated_at = NOW()
        WHERE id = ?
        """,
        blankToNull(request.categoryName()),
        request.isEnabled() == null ? null : (request.isEnabled() ? 1 : 0),
        request.sortOrder(),
        id);
  }

  public int deleteCategory(long id) {
    return jdbcTemplate.update("DELETE FROM tag_categories WHERE id = ? AND is_builtin = 0", id);
  }

  public long createValue(TagValueRequest request, int sortOrder) {
    jdbcTemplate.update("""
        INSERT INTO tag_values (category_id, tag_value, display_name, is_enabled, sort_order)
        VALUES (?, ?, ?, 1, ?)
        """, request.categoryId(), request.tagValue().trim(), request.displayName().trim(), sortOrder);
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0L : id;
  }

  public void updateValue(long id, TagValueRequest request) {
    jdbcTemplate.update("""
        UPDATE tag_values
        SET display_name = COALESCE(?, display_name),
            is_enabled = COALESCE(?, is_enabled),
            sort_order = COALESCE(?, sort_order),
            updated_at = NOW()
        WHERE id = ?
        """,
        blankToNull(request.displayName()),
        request.isEnabled() == null ? null : (request.isEnabled() ? 1 : 0),
        request.sortOrder(),
        id);
  }

  public void toggleValue(long id, boolean enabled) {
    jdbcTemplate.update("UPDATE tag_values SET is_enabled = ?, updated_at = NOW() WHERE id = ?", enabled ? 1 : 0, id);
  }

  public int deleteValue(long id) {
    return jdbcTemplate.update("DELETE FROM tag_values WHERE id = ?", id);
  }

  public int usageCount(String boundField, String tagValue) {
    List<Object> args = new ArrayList<>();
    StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM customers WHERE ");
    List<String> clauses = new ArrayList<>();
    addExact(clauses, args, "personality_type", tagValue);
    addLike(clauses, args, "body_concerns", tagValue);
    addLike(clauses, args, "worries", tagValue);
    String column = toSnakeCase(boundField);
    if (!List.of("personality_type", "body_concerns", "worries").contains(column)) {
      addExact(clauses, args, column, tagValue);
    }
    sql.append(String.join(" OR ", clauses));
    Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
    return count == null ? 0 : count;
  }

  public List<TagValue> findEnabledForPrompt() {
    return jdbcTemplate.query("""
        SELECT v.id, v.category_id, c.category_key, v.tag_value, v.display_name, v.is_enabled, v.sort_order, v.created_at, v.updated_at
        FROM tag_categories c
        JOIN tag_values v ON c.id = v.category_id
        WHERE c.is_enabled = 1 AND v.is_enabled = 1
        ORDER BY c.sort_order ASC, v.sort_order ASC, v.id ASC
        """, this::mapValue);
  }

  private List<TagValue> values(long categoryId) {
    return jdbcTemplate.query("""
        SELECT v.id, v.category_id, c.category_key, v.tag_value, v.display_name, v.is_enabled, v.sort_order, v.created_at, v.updated_at
        FROM tag_values v JOIN tag_categories c ON c.id = v.category_id
        WHERE v.category_id = ?
        ORDER BY v.sort_order ASC, v.id ASC
        """, this::mapValue, categoryId);
  }

  private TagCategory mapCategory(ResultSet rs, List<TagValue> values) throws SQLException {
    return new TagCategory(
        rs.getLong("id"),
        rs.getString("category_key"),
        rs.getString("category_name"),
        rs.getString("bound_field"),
        rs.getInt("is_builtin") == 1,
        rs.getInt("is_enabled") == 1,
        rs.getInt("sort_order"),
        values,
        rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime());
  }

  private TagValue mapValue(ResultSet rs, int rowNum) throws SQLException {
    return new TagValue(
        rs.getLong("id"),
        rs.getLong("category_id"),
        rs.getString("category_key"),
        rs.getString("tag_value"),
        rs.getString("display_name"),
        rs.getInt("is_enabled") == 1,
        rs.getInt("sort_order"),
        rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime());
  }

  private void addExact(List<String> clauses, List<Object> args, String column, String value) {
    clauses.add(column + " = ?");
    args.add(value);
  }

  private void addLike(List<String> clauses, List<Object> args, String column, String value) {
    clauses.add(column + " LIKE CONCAT('%', ?, '%')");
    args.add(value);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String toSnakeCase(String camel) {
    return camel.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
  }
}
