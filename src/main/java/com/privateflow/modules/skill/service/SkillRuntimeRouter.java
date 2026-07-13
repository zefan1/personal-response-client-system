package com.privateflow.modules.skill.service;

import com.privateflow.modules.customer.LeadTypes;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillErrorCodes;
import com.privateflow.modules.skill.SkillGatewayException;
import com.privateflow.modules.skill.config.SkillConfig;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SkillRuntimeRouter {

  private final JdbcTemplate jdbcTemplate;

  public SkillRuntimeRouter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<String> route(Scene scene, String leadType, SkillConfig config) {
    String normalizedLeadType = normalizeLeadType(leadType);
    Optional<String> exact = configuredRoute(scene, normalizedLeadType);
    if (exact.isPresent()) {
      return exact;
    }
    Optional<String> general = configuredRoute(scene, "GENERAL");
    if (general.isPresent()) {
      return general;
    }
    return Optional.ofNullable(fallbackSkillId(normalizedLeadType, config))
        .filter(value -> !value.isBlank());
  }

  private Optional<String> configuredRoute(Scene scene, String leadType) {
    Long configuredCount = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM skill_scene_bindings
        WHERE scene = ? AND lead_type = ?
        """, Long.class, scene.name(), leadType);
    if (configuredCount != null && configuredCount > 0) {
      return jdbcTemplate.query("""
          SELECT skill_id FROM skill_scene_bindings
          WHERE scene = ? AND lead_type = ? AND enabled = 1
          ORDER BY priority ASC, id ASC
          LIMIT 1
          """, (rs, rowNum) -> rs.getString("skill_id"), scene.name(), leadType)
          .stream()
          .findFirst()
          .or(() -> {
            throw new SkillGatewayException(
                SkillErrorCodes.SKILL_ROUTE_NOT_CONFIGURED,
                "当前场景和线索类型没有启用的 Skill 绑定",
                false,
                false);
          });
    }
    return Optional.empty();
  }

  private String fallbackSkillId(String leadType, SkillConfig config) {
    if (LeadTypes.TUAN_GOU.equals(leadType)) {
      return config.tuanSkillGroupId();
    }
    if (LeadTypes.XIAN_SUO.equals(leadType)) {
      return config.xiansuoSkillGroupId();
    }
    return config.defaultSkillId();
  }

  private String normalizeLeadType(String leadType) {
    String normalized = LeadTypes.normalize(leadType);
    return LeadTypes.TUAN_GOU.equals(normalized) || LeadTypes.XIAN_SUO.equals(normalized) || LeadTypes.PENDING.equals(normalized)
        ? normalized
        : LeadTypes.PENDING;
  }
}
