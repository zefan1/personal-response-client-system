package com.privateflow.modules.llm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class LlmRouteRepository {

  private static final RowMapper<LlmSceneRoute> ROW_MAPPER = new RouteRowMapper();
  private final JdbcTemplate jdbcTemplate;

  public LlmRouteRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<LlmSceneRoute> findAll(LlmScene scene, String leadType) {
    List<Object> args = new ArrayList<>();
    StringBuilder where = new StringBuilder("WHERE 1=1");
    if (scene != null) {
      where.append(" AND r.scene = ?");
      args.add(scene.name());
    }
    if (leadType != null && !leadType.isBlank()) {
      where.append(" AND r.lead_type = ?");
      args.add(leadType.trim());
    }
    return jdbcTemplate.query("""
        SELECT r.*, e.env_name, e.model, e.protocol
        FROM llm_scene_routes r
        LEFT JOIN llm_environments e ON e.id = r.llm_environment_id
        """ + where + " ORDER BY r.scene ASC, r.lead_type ASC, r.priority ASC, r.id ASC",
        ROW_MAPPER,
        args.toArray());
  }

  public Optional<LlmSceneRoute> findById(long id) {
    return jdbcTemplate.query("""
        SELECT r.*, e.env_name, e.model, e.protocol
        FROM llm_scene_routes r
        LEFT JOIN llm_environments e ON e.id = r.llm_environment_id
        WHERE r.id = ?
        LIMIT 1
        """, ROW_MAPPER, id).stream().findFirst();
  }

  public Optional<LlmSceneRoute> findEnabled(LlmScene scene, String leadType) {
    return jdbcTemplate.query("""
        SELECT r.*, e.env_name, e.model, e.protocol
        FROM llm_scene_routes r
        LEFT JOIN llm_environments e ON e.id = r.llm_environment_id
        WHERE r.scene = ? AND r.lead_type = ? AND r.enabled = 1
        ORDER BY r.priority ASC, r.id ASC
        LIMIT 1
        """, ROW_MAPPER, scene.name(), leadType == null ? "" : leadType).stream().findFirst();
  }

  public List<LlmSceneRoute> findEnabledCandidates(LlmScene scene, String leadType) {
    return jdbcTemplate.query("""
        SELECT r.*, e.env_name, e.model, e.protocol
        FROM llm_scene_routes r
        LEFT JOIN llm_environments e ON e.id = r.llm_environment_id
        WHERE r.scene = ? AND r.lead_type = ? AND r.enabled = 1
        ORDER BY r.priority ASC, r.id ASC
        """, ROW_MAPPER, scene.name(), leadType == null ? "" : leadType);
  }

  public long create(LlmRouteRequest request, String leadType, boolean enabled) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      var statement = connection.prepareStatement("""
          INSERT INTO llm_scene_routes (scene, lead_type, llm_environment_id, priority, enabled)
          VALUES (?, ?, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      statement.setString(1, request.scene().name());
      statement.setString(2, leadType);
      statement.setLong(3, request.environmentId());
      statement.setInt(4, priority(request.priority()));
      statement.setInt(5, enabled ? 1 : 0);
      return statement;
    }, keyHolder);
    Map<String, Object> keys = keyHolder.getKeys();
    if (keys != null && keys.get("id") instanceof Number id) {
      return id.longValue();
    }
    Number key = keyHolder.getKey();
    return key == null ? 0L : key.longValue();
  }

  public void update(long id, LlmRouteRequest request, String leadType, boolean enabled) {
    jdbcTemplate.update("""
        UPDATE llm_scene_routes
        SET scene = ?, lead_type = ?, llm_environment_id = ?, priority = ?, enabled = ?, updated_at = NOW()
        WHERE id = ?
        """,
        request.scene().name(),
        leadType,
        request.environmentId(),
        priority(request.priority()),
        enabled ? 1 : 0,
        id);
  }

  public int delete(long id) {
    return jdbcTemplate.update("DELETE FROM llm_scene_routes WHERE id = ?", id);
  }

  public void toggle(long id, boolean enabled) {
    jdbcTemplate.update("UPDATE llm_scene_routes SET enabled = ?, updated_at = NOW() WHERE id = ?", enabled ? 1 : 0, id);
  }

  public boolean existsInGroup(LlmScene scene, String leadType, long environmentId, Long exceptId) {
    List<Object> args = new ArrayList<>();
    args.add(scene.name());
    args.add(leadType);
    args.add(environmentId);
    String except = "";
    if (exceptId != null) {
      except = " AND id <> ?";
      args.add(exceptId);
    }
    Long count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM llm_scene_routes
        WHERE scene = ? AND lead_type = ? AND llm_environment_id = ?
        """ + except, Long.class, args.toArray());
    return count != null && count > 0;
  }

  private static int priority(Integer priority) {
    return priority == null ? 0 : priority;
  }

  private static final class RouteRowMapper implements RowMapper<LlmSceneRoute> {
    @Override
    public LlmSceneRoute mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new LlmSceneRoute(
          rs.getLong("id"),
          LlmScene.valueOf(rs.getString("scene")),
          rs.getString("lead_type"),
          rs.getLong("llm_environment_id"),
          rs.getString("env_name"),
          rs.getString("model"),
          rs.getString("protocol"),
          rs.getInt("priority"),
          rs.getInt("enabled") == 1,
          rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
          rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime());
    }
  }
}
