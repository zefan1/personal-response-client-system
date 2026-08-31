package com.privateflow.modules.skill.service;

import com.privateflow.modules.api.security.SecretCipher;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SkillSceneConnectionResolver {

  private final JdbcTemplate jdbcTemplate;
  private final SecretCipher secretCipher;

  public SkillSceneConnectionResolver(JdbcTemplate jdbcTemplate, SecretCipher secretCipher) {
    this.jdbcTemplate = jdbcTemplate;
    this.secretCipher = secretCipher;
  }

  public Optional<SkillConnection> resolve(Map<String, Object> payload) {
    String scene = value(payload.get("scene"));
    String leadType = value(payload.get("lead_type"));
    if (scene.isBlank()) {
      return Optional.empty();
    }
    return jdbcTemplate.query("""
        SELECT skill_base_url, skill_api_key, skill_protocol
        FROM skill_scene_bindings
        WHERE scene = ? AND enabled = 1
          AND (lead_type = ? OR lead_type = 'GENERAL')
        ORDER BY CASE WHEN lead_type = ? THEN 0 ELSE 1 END, priority ASC, id ASC
        LIMIT 1
        """, (rs, rowNum) -> new SkillConnection(
            rs.getString("skill_base_url"),
            secretCipher.decrypt(rs.getString("skill_api_key")),
            rs.getString("skill_protocol")), scene, leadType, leadType)
        .stream()
        .filter(SkillConnection::configured)
        .findFirst();
  }

  private String value(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }
}
