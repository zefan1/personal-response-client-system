package com.privateflow.modules.api.ai;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PromptVersionRepository {

  private static final String VERSION_INDEX = "idx_config_key_version";
  private final JdbcTemplate jdbcTemplate;

  public PromptVersionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<PromptVersion> list(String configKey) {
    return jdbcTemplate.query("""
        SELECT version, content, operator, is_stable, change_note, created_at
        FROM skill_prompt_versions
        WHERE config_key = ?
        ORDER BY version DESC
        LIMIT 50
        """, (rs, rowNum) -> new PromptVersion(
            rs.getInt("version"),
            rs.getString("content"),
            rs.getString("operator"),
            rs.getInt("is_stable") == 1,
            rs.getString("change_note"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime()), configKey);
  }

  public Optional<PromptVersion> find(String configKey, int version) {
    return jdbcTemplate.query("""
        SELECT version, content, operator, is_stable, change_note, created_at
        FROM skill_prompt_versions
        WHERE config_key = ? AND version = ?
        LIMIT 1
        """, (rs, rowNum) -> new PromptVersion(
            rs.getInt("version"),
            rs.getString("content"),
            rs.getString("operator"),
            rs.getInt("is_stable") == 1,
            rs.getString("change_note"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime()), configKey, version)
        .stream().findFirst();
  }

  public int currentVersion(String configKey) {
    Integer version = jdbcTemplate.queryForObject(
        "SELECT COALESCE(MAX(version), 0) FROM skill_prompt_versions WHERE config_key = ?",
        Integer.class,
        configKey);
    return version == null ? 0 : version;
  }

  public int create(String configKey, String content, String operator, String note) {
    int next = currentVersion(configKey) + 1;
    jdbcTemplate.update("""
        INSERT INTO skill_prompt_versions (config_key, version, content, operator, change_note)
        VALUES (?, ?, ?, ?, ?)
        """, configKey, next, content, operator == null || operator.isBlank() ? "SYSTEM" : operator, note);
    return next;
  }

  public void updateConfig(String configKey, String value) {
    jdbcTemplate.update("""
        UPDATE system_configs SET config_value = ?, updated_at = NOW()
        WHERE config_key = ?
        """, value, configKey);
  }
}
