package com.privateflow.modules.customer.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IntentProjectMappingRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public IntentProjectMappingRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public List<IntentProjectMappingRule> list() {
    return jdbcTemplate.query("""
        SELECT id, option_id, option_text, keywords_json, priority, status, source_field,
               last_seen_at, updated_at
        FROM intent_project_mapping_rules
        ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'PENDING' THEN 1 ELSE 2 END,
                 priority DESC, option_text, id
        """, (rs, rowNum) -> rule(
        rs.getLong("id"), rs.getString("option_id"), rs.getString("option_text"),
        rs.getString("keywords_json"), rs.getInt("priority"), rs.getString("status"),
        rs.getString("source_field"), rs.getTimestamp("last_seen_at"), rs.getTimestamp("updated_at")));
  }

  public boolean exists() {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM intent_project_mapping_rules", Integer.class);
    return count != null && count > 0;
  }

  public Optional<IntentProjectMappingRule> find(String optionId) {
    return list().stream().filter(item -> item.optionId().equals(optionId)).findFirst();
  }

  public void observe(String optionId, String optionText, boolean active) {
    jdbcTemplate.update("""
        INSERT INTO intent_project_mapping_rules
          (option_id, option_text, keywords_json, priority, status, source_field, last_seen_at)
        VALUES (?, ?, '[]', 0, ?, '意向项目', CURRENT_TIMESTAMP)
        ON DUPLICATE KEY UPDATE
          option_text = VALUES(option_text),
          status = CASE WHEN status = 'ORPHANED' THEN VALUES(status) ELSE status END,
          last_seen_at = CURRENT_TIMESTAMP,
          updated_at = CURRENT_TIMESTAMP
        """, optionId, optionText, active ? "ACTIVE" : "PENDING");
  }

  public void markMissing(List<String> optionIds) {
    if (optionIds == null || optionIds.isEmpty()) {
      jdbcTemplate.update("UPDATE intent_project_mapping_rules SET status = 'ORPHANED', updated_at = CURRENT_TIMESTAMP WHERE status IN ('ACTIVE','PENDING')");
      return;
    }
    String placeholders = String.join(",", optionIds.stream().map(ignored -> "?").toList());
    Object[] args = optionIds.toArray();
    jdbcTemplate.update("UPDATE intent_project_mapping_rules SET status = 'ORPHANED', updated_at = CURRENT_TIMESTAMP WHERE status IN ('ACTIVE','PENDING','DISABLED') AND option_id NOT IN (" + placeholders + ")", args);
  }

  public IntentProjectMappingRule save(String optionId, List<String> keywords, int priority, boolean enabled) {
    String json;
    try {
      json = objectMapper.writeValueAsString(keywords == null ? List.of() : keywords);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("关键词规则保存失败", ex);
    }
    jdbcTemplate.update("""
        UPDATE intent_project_mapping_rules
        SET keywords_json = ?, priority = ?, status = ?, updated_at = CURRENT_TIMESTAMP
        WHERE option_id = ?
        """, json, priority, enabled ? "ACTIVE" : "DISABLED", optionId);
    return find(optionId).orElseThrow(() -> new IllegalArgumentException("意向项目选项不存在：" + optionId));
  }

  private IntentProjectMappingRule rule(long id, String optionId, String optionText, String keywordsJson,
      int priority, String status, String sourceField, Timestamp lastSeenAt, Timestamp updatedAt) {
    List<String> keywords;
    try {
      keywords = objectMapper.readValue(keywordsJson == null ? "[]" : keywordsJson, new TypeReference<>() {});
    } catch (Exception ex) {
      keywords = List.of();
    }
    return new IntentProjectMappingRule(id, optionId, optionText, keywords, priority, status, sourceField,
        localDateTime(lastSeenAt), localDateTime(updatedAt));
  }

  private LocalDateTime localDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
