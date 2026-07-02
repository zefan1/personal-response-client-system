package com.privateflow.modules.followup.infra;

import com.privateflow.modules.followup.ActionType;
import com.privateflow.modules.followup.FollowupRule;
import com.privateflow.modules.followup.RulePage;
import com.privateflow.modules.followup.RuleRequest;
import com.privateflow.modules.followup.RuleSearchCriteria;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FollowupRuleRepository {

  private static final RowMapper<FollowupRule> ROW_MAPPER = new RuleRowMapper();
  private final JdbcTemplate jdbcTemplate;

  public FollowupRuleRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<FollowupRule> findEnabled() {
    return jdbcTemplate.query(
        "SELECT * FROM followup_rules WHERE enabled = 1 ORDER BY priority DESC",
        ROW_MAPPER);
  }

  public Optional<FollowupRule> findById(long id) {
    return jdbcTemplate.query("SELECT * FROM followup_rules WHERE id = ? LIMIT 1", ROW_MAPPER, id)
        .stream().findFirst();
  }

  public boolean nameExists(String name, Long excludeId) {
    List<Object> args = new ArrayList<>();
    StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM followup_rules WHERE name = ?");
    args.add(name);
    if (excludeId != null) {
      sql.append(" AND id <> ?");
      args.add(excludeId);
    }
    Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
    return count != null && count > 0;
  }

  public RulePage search(RuleSearchCriteria criteria) {
    int page = Math.max(1, criteria.page());
    int size = Math.max(1, Math.min(criteria.size(), 100));
    List<Object> args = new ArrayList<>();
    String where = buildWhere(criteria, args);
    Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM followup_rules " + where, Long.class, args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add((page - 1) * size);
    pageArgs.add(size);
    List<FollowupRule> items = jdbcTemplate.query(
        "SELECT * FROM followup_rules " + where + " ORDER BY priority DESC, id DESC LIMIT ?, ?",
        ROW_MAPPER,
        pageArgs.toArray());
    return new RulePage(page, size, total == null ? 0 : total, items);
  }

  public long create(RuleRequest request) {
    jdbcTemplate.update("""
        INSERT INTO followup_rules (name, condition_json, action_type, action_config, priority, enabled, is_builtin)
        VALUES (?, ?, ?, ?, ?, ?, 0)
        """,
        request.name(),
        request.conditionJson(),
        request.actionType().name(),
        request.actionConfig(),
        request.priority(),
        Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0L : id;
  }

  public void update(long id, RuleRequest request, boolean keepIdentityFields) {
    if (keepIdentityFields) {
      jdbcTemplate.update("""
          UPDATE followup_rules
          SET condition_json = ?, action_config = ?, priority = ?, enabled = ?, updated_at = NOW()
          WHERE id = ?
          """,
          request.conditionJson(),
          request.actionConfig(),
          request.priority(),
          Boolean.FALSE.equals(request.enabled()) ? 0 : 1,
          id);
    } else {
      jdbcTemplate.update("""
          UPDATE followup_rules
          SET name = ?, condition_json = ?, action_type = ?, action_config = ?, priority = ?, enabled = ?, updated_at = NOW()
          WHERE id = ?
          """,
          request.name(),
          request.conditionJson(),
          request.actionType().name(),
          request.actionConfig(),
          request.priority(),
          Boolean.FALSE.equals(request.enabled()) ? 0 : 1,
          id);
    }
  }

  public int delete(long id) {
    return jdbcTemplate.update("DELETE FROM followup_rules WHERE id = ? AND is_builtin = 0", id);
  }

  public int toggle(long id, boolean enabled) {
    return jdbcTemplate.update("UPDATE followup_rules SET enabled = ?, updated_at = NOW() WHERE id = ?", enabled ? 1 : 0, id);
  }

  private String buildWhere(RuleSearchCriteria criteria, List<Object> args) {
    StringBuilder where = new StringBuilder("WHERE 1=1");
    if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
      where.append(" AND name LIKE CONCAT('%', ?, '%')");
      args.add(criteria.keyword().trim());
    }
    if (criteria.actionType() != null) {
      where.append(" AND action_type = ?");
      args.add(criteria.actionType().name());
    }
    if (criteria.enabled() != null) {
      where.append(" AND enabled = ?");
      args.add(criteria.enabled() ? 1 : 0);
    }
    return where.toString();
  }

  private static final class RuleRowMapper implements RowMapper<FollowupRule> {
    @Override
    public FollowupRule mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new FollowupRule(
          rs.getLong("id"),
          rs.getString("name"),
          rs.getString("condition_json"),
          ActionType.valueOf(rs.getString("action_type")),
          rs.getString("action_config"),
          rs.getInt("priority"),
          rs.getInt("enabled") == 1,
          rs.getInt("is_builtin") == 1,
          rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
          rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime());
    }
  }
}
