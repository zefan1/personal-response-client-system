package com.privateflow.modules.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillErrorCodes;
import com.privateflow.modules.skill.SkillGatewayException;
import com.privateflow.modules.skill.config.SkillConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SkillRuntimeRouterTest {

  private JdbcTemplate jdbcTemplate;
  private SkillRuntimeRouter router;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:skill_router;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS skill_scene_bindings");
    jdbcTemplate.execute("""
        CREATE TABLE skill_scene_bindings (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          skill_id VARCHAR(100) NOT NULL,
          skill_name VARCHAR(100),
          scene VARCHAR(50) NOT NULL,
          lead_type VARCHAR(20) NOT NULL,
          priority INT NOT NULL DEFAULT 0,
          enabled TINYINT NOT NULL DEFAULT 1
        )
        """);
    router = new SkillRuntimeRouter(jdbcTemplate);
  }

  @Test
  void routesByEnabledBindingPriorityBeforeLegacyFallback() {
    jdbcTemplate.update("""
        INSERT INTO skill_scene_bindings (skill_id, skill_name, scene, lead_type, priority, enabled)
        VALUES ('skill-b', 'B', 'ACTIVE_REPLY', 'TUAN_GOU', 20, 1),
               ('skill-a', 'A', 'ACTIVE_REPLY', 'TUAN_GOU', 10, 1)
        """);

    assertThat(router.route(Scene.ACTIVE_REPLY, "TUAN_GOU", config()).orElseThrow()).isEqualTo("skill-a");
  }

  @Test
  void configuredButDisabledRouteFailsExplicitly() {
    jdbcTemplate.update("""
        INSERT INTO skill_scene_bindings (skill_id, skill_name, scene, lead_type, priority, enabled)
        VALUES ('skill-a', 'A', 'ACTIVE_REPLY', 'TUAN_GOU', 10, 0)
        """);

    assertThatThrownBy(() -> router.route(Scene.ACTIVE_REPLY, "TUAN_GOU", config()))
        .isInstanceOf(SkillGatewayException.class)
        .extracting(ex -> ((SkillGatewayException) ex).getErrorCode())
        .isEqualTo(SkillErrorCodes.SKILL_ROUTE_NOT_CONFIGURED);
  }

  @Test
  void fallsBackToLegacySkillGroupWhenNoBindingExists() {
    assertThat(router.route(Scene.ACTIVE_REPLY, "TUAN_GOU", config()).orElseThrow()).isEqualTo("legacy-tuan");
  }

  private SkillConfig config() {
    return new SkillConfig(
        "",
        "",
        "LAST_FOUR",
        "",
        10000,
        30,
        0.5,
        5,
        30,
        "fallback",
        "legacy-tuan",
        "legacy-xiansuo",
        "legacy-default",
        "prompt",
        "",
        0.3,
        15,
        8000,
        3);
  }
}
