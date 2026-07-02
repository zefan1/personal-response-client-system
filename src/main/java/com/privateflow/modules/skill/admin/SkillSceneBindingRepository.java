package com.privateflow.modules.skill.admin;

import com.privateflow.modules.skill.Scene;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SkillSceneBindingRepository {

  private static final String GROUP_INDEX = "idx_scene_lead";
  private static final RowMapper<SkillSceneBinding> ROW_MAPPER = new BindingRowMapper();
  private final JdbcTemplate jdbcTemplate;

  public SkillSceneBindingRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<SkillSceneBinding> findAll(Scene scene, String leadType) {
    List<Object> args = new ArrayList<>();
    StringBuilder where = new StringBuilder("WHERE 1=1");
    if (scene != null) {
      where.append(" AND scene = ?");
      args.add(scene.name());
    }
    if (leadType != null && !leadType.isBlank()) {
      where.append(" AND lead_type = ?");
      args.add(leadType.trim());
    }
    return jdbcTemplate.query(
        "SELECT * FROM skill_scene_bindings " + where + " ORDER BY scene ASC, lead_type ASC, priority ASC, id ASC",
        ROW_MAPPER,
        args.toArray());
  }

  public Optional<SkillSceneBinding> findById(long id) {
    return jdbcTemplate.query("SELECT * FROM skill_scene_bindings WHERE id = ? LIMIT 1", ROW_MAPPER, id)
        .stream().findFirst();
  }

  public boolean existsInGroup(String skillId, Scene scene, String leadType, Long exceptId) {
    List<Object> args = new ArrayList<>();
    args.add(skillId);
    args.add(scene.name());
    args.add(leadType);
    String except = "";
    if (exceptId != null) {
      except = " AND id <> ?";
      args.add(exceptId);
    }
    Long count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM skill_scene_bindings
        WHERE skill_id = ? AND scene = ? AND lead_type = ?
        """ + except, Long.class, args.toArray());
    return count != null && count > 0;
  }

  public long create(SkillBindingRequest request) {
    jdbcTemplate.update("""
        INSERT INTO skill_scene_bindings (skill_id, skill_name, scene, lead_type, priority, enabled)
        VALUES (?, ?, ?, ?, ?, 1)
        """,
        request.skillId().trim(),
        request.skillName().trim(),
        request.scene().name(),
        normalizedLeadType(request.leadType()),
        request.priority() == null ? 0 : request.priority());
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0L : id;
  }

  public void update(long id, SkillBindingRequest request) {
    jdbcTemplate.update("""
        UPDATE skill_scene_bindings
        SET skill_id = ?, skill_name = ?, scene = ?, lead_type = ?, priority = ?, updated_at = NOW()
        WHERE id = ?
        """,
        request.skillId().trim(),
        request.skillName().trim(),
        request.scene().name(),
        normalizedLeadType(request.leadType()),
        request.priority() == null ? 0 : request.priority(),
        id);
  }

  public int delete(long id) {
    return jdbcTemplate.update("DELETE FROM skill_scene_bindings WHERE id = ?", id);
  }

  public void toggle(long id, boolean enabled) {
    jdbcTemplate.update("UPDATE skill_scene_bindings SET enabled = ?, updated_at = NOW() WHERE id = ?", enabled ? 1 : 0, id);
  }

  public long countEnabledPeers(SkillSceneBinding binding) {
    Long count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM skill_scene_bindings
        WHERE scene = ? AND lead_type = ? AND enabled = 1 AND id <> ?
        """, Long.class, binding.scene().name(), binding.leadType(), binding.id());
    return count == null ? 0 : count;
  }

  public void markTested(long id) {
    jdbcTemplate.update("UPDATE skill_scene_bindings SET last_tested_at = NOW(), updated_at = NOW() WHERE id = ?", id);
  }

  private static String normalizedLeadType(String leadType) {
    return leadType == null ? "" : leadType.trim();
  }

  private static final class BindingRowMapper implements RowMapper<SkillSceneBinding> {
    @Override
    public SkillSceneBinding mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new SkillSceneBinding(
          rs.getLong("id"),
          rs.getString("skill_id"),
          rs.getString("skill_name"),
          Scene.valueOf(rs.getString("scene")),
          rs.getString("lead_type"),
          rs.getInt("priority"),
          rs.getInt("enabled") == 1,
          rs.getTimestamp("last_tested_at") == null ? null : rs.getTimestamp("last_tested_at").toLocalDateTime(),
          rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
          rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime());
    }
  }
}
