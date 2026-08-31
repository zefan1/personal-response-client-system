package com.privateflow.modules.skill.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.privateflow.modules.api.security.SecretCipher;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SkillSceneConnectionResolverTest {

  private JdbcTemplate jdbcTemplate;
  private SecretCipher secretCipher;
  private SkillSceneConnectionResolver resolver;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:skill_connection;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    secretCipher = new SecretCipher("test-secret");
    jdbcTemplate.execute("DROP TABLE IF EXISTS skill_scene_bindings");
    jdbcTemplate.execute("""
        CREATE TABLE skill_scene_bindings (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          skill_id VARCHAR(100) NOT NULL,
          scene VARCHAR(50) NOT NULL,
          lead_type VARCHAR(20) NOT NULL,
          priority INT NOT NULL DEFAULT 10,
          enabled TINYINT NOT NULL DEFAULT 1,
          skill_base_url VARCHAR(500),
          skill_api_key VARCHAR(1000),
          skill_protocol VARCHAR(50)
        )
        """);
    resolver = new SkillSceneConnectionResolver(jdbcTemplate, secretCipher);
  }

  @Test
  void resolvesTheDirectSceneConnectionAndDecryptsItsKey() {
    jdbcTemplate.update("""
        INSERT INTO skill_scene_bindings
          (skill_id, scene, lead_type, priority, enabled, skill_base_url, skill_api_key, skill_protocol)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, "scene-opening-general", "OPENING", "GENERAL", 10, 1,
        "https://opening-skill.example.com", secretCipher.encrypt("opening-secret"), "MCP_STREAMABLE_HTTP");

    SkillConnection connection = resolver.resolve(Map.of(
        "scene", "OPENING",
        "lead_type", "TUAN_GOU",
        "skill_id", "sales-champion-coach"))
        .orElseThrow();

    assertThat(connection.baseUrl()).isEqualTo("https://opening-skill.example.com");
    assertThat(connection.apiKey()).isEqualTo("opening-secret");
    assertThat(connection.protocol()).isEqualTo("MCP_STREAMABLE_HTTP");
  }
}
