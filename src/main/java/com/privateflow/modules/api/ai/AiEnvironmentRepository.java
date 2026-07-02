package com.privateflow.modules.api.ai;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AiEnvironmentRepository {

  private static final String ACTIVE_INDEX = "idx_provider_active";
  private final JdbcTemplate jdbcTemplate;

  public AiEnvironmentRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<AiEnvironment> list(AiEnvironmentType type) {
    return jdbcTemplate.query("SELECT * FROM " + table(type) + " ORDER BY is_active DESC, id DESC", mapper(type));
  }

  public Optional<AiEnvironment> find(AiEnvironmentType type, long id) {
    return jdbcTemplate.query("SELECT * FROM " + table(type) + " WHERE id = ? LIMIT 1", mapper(type), id).stream().findFirst();
  }

  public long create(AiEnvironmentType type, AiEnvironmentRequest request) {
    jdbcTemplate.update("""
        INSERT INTO %s (env_name, provider, base_url, api_key, api_key_last4, is_active)
        VALUES (?, ?, ?, ?, ?, 0)
        """.formatted(table(type)),
        request.envName().trim(),
        type.provider(),
        request.baseUrl().trim(),
        encrypt(request.apiKey().trim()),
        last4(request.apiKey().trim()));
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0 : id;
  }

  public void update(AiEnvironmentType type, long id, AiEnvironmentRequest request) {
    jdbcTemplate.update("""
        UPDATE %s
        SET env_name = ?, base_url = ?, api_key = ?, api_key_last4 = ?, updated_at = NOW()
        WHERE id = ?
        """.formatted(table(type)),
        request.envName().trim(),
        request.baseUrl().trim(),
        encrypt(request.apiKey().trim()),
        last4(request.apiKey().trim()),
        id);
  }

  public void activate(AiEnvironmentType type, AiEnvironment environment) {
    jdbcTemplate.update("UPDATE " + table(type) + " SET is_active = 0 WHERE provider = ?", type.provider());
    jdbcTemplate.update("UPDATE " + table(type) + " SET is_active = 1, updated_at = NOW() WHERE id = ?", environment.id());
  }

  public int delete(AiEnvironmentType type, long id) {
    return jdbcTemplate.update("DELETE FROM " + table(type) + " WHERE id = ? AND is_active = 0", id);
  }

  public long count(AiEnvironmentType type) {
    Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table(type), Long.class);
    return count == null ? 0 : count;
  }

  public void markImageTest(long id, boolean ok) {
    jdbcTemplate.update("""
        UPDATE image_environments
        SET last_test_at = NOW(), last_test_ok = ?, updated_at = NOW()
        WHERE id = ?
        """, ok ? 1 : 0, id);
  }

  public void updateConfig(String key, String value) {
    int updated = jdbcTemplate.update("""
        UPDATE system_configs SET config_value = ?, updated_at = NOW()
        WHERE config_key = ?
        """, value, key);
    if (updated == 0) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "unknown config key");
    }
  }

  public String encryptedApiKey(AiEnvironmentType type, long id) {
    return jdbcTemplate.query("SELECT api_key FROM " + table(type) + " WHERE id = ? LIMIT 1",
        (rs, rowNum) -> rs.getString("api_key"), id).stream().findFirst()
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "environment not found"));
  }

  private static String table(AiEnvironmentType type) {
    return type == AiEnvironmentType.SKILL ? "skill_environments" : "image_environments";
  }

  private RowMapper<AiEnvironment> mapper(AiEnvironmentType type) {
    return (rs, rowNum) -> map(type, rs);
  }

  private AiEnvironment map(AiEnvironmentType type, ResultSet rs) throws SQLException {
    return new AiEnvironment(
        rs.getLong("id"),
        rs.getString("env_name"),
        rs.getString("provider"),
        rs.getString("base_url"),
        rs.getString("api_key_last4"),
        rs.getInt("is_active") == 1,
        type == AiEnvironmentType.IMAGE && hasColumn(rs, "last_test_at") && rs.getTimestamp("last_test_at") != null
            ? rs.getTimestamp("last_test_at").toLocalDateTime() : null,
        type == AiEnvironmentType.IMAGE && hasColumn(rs, "last_test_ok") && rs.getObject("last_test_ok") != null
            ? rs.getInt("last_test_ok") == 1 : null,
        rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime());
  }

  private boolean hasColumn(ResultSet rs, String column) {
    try {
      rs.findColumn(column);
      return true;
    } catch (SQLException ex) {
      return false;
    }
  }

  static String encrypt(String value) {
    return "{plain}" + value;
  }

  static String decrypt(String value) {
    return value == null ? "" : value.replaceFirst("^\\{plain}", "");
  }

  private static String last4(String value) {
    return value.length() <= 4 ? value : value.substring(value.length() - 4);
  }
}
