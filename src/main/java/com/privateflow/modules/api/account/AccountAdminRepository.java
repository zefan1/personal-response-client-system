package com.privateflow.modules.api.account;

import com.privateflow.modules.api.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountAdminRepository {

  private final JdbcTemplate jdbcTemplate;

  public AccountAdminRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<AccountAdminItem> list(int page, int pageSize, Role role, String keyword, Boolean enabled) {
    List<Object> args = new ArrayList<>();
    String where = where(role, keyword, enabled, args);
    args.add(pageSize);
    args.add((page - 1) * pageSize);
    return jdbcTemplate.query("""
        SELECT a.id, COALESCE(a.phone, a.username) AS phone, a.display_name, a.role, a.leader_id,
               l.display_name AS leader_name, a.is_enabled, a.last_login_at, a.created_at
        FROM accounts a
        LEFT JOIN accounts l ON a.leader_id = l.id
        """ + where + """
        ORDER BY a.created_at DESC, a.id DESC
        LIMIT ? OFFSET ?
        """, this::map, args.toArray());
  }

  public long count(Role role, String keyword, Boolean enabled) {
    List<Object> args = new ArrayList<>();
    String where = where(role, keyword, enabled, args);
    Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts a " + where, Long.class, args.toArray());
    return total == null ? 0L : total;
  }

  public Optional<AccountAdminItem> find(long id) {
    return jdbcTemplate.query("""
        SELECT a.id, COALESCE(a.phone, a.username) AS phone, a.display_name, a.role, a.leader_id,
               l.display_name AS leader_name, a.is_enabled, a.last_login_at, a.created_at
        FROM accounts a
        LEFT JOIN accounts l ON a.leader_id = l.id
        WHERE a.id = ?
        LIMIT 1
        """, this::map, id).stream().findFirst();
  }

  public boolean phoneExists(String phone) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM accounts WHERE phone = ? OR username = ?",
        Integer.class,
        phone,
        phone);
    return count != null && count > 0;
  }

  public boolean enabledLeaderExists(long leaderId) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM accounts WHERE id = ? AND role = 'LEADER' AND is_enabled = 1",
        Integer.class,
        leaderId);
    return count != null && count > 0;
  }

  public long create(AccountCreateRequest request, String passwordHash) {
    jdbcTemplate.update("""
        INSERT INTO accounts (phone, username, password_hash, display_name, role, leader_id, is_enabled)
        VALUES (?, ?, ?, ?, ?, ?, 1)
        """,
        request.phone().trim(),
        request.phone().trim(),
        passwordHash,
        request.displayName().trim(),
        request.role().name(),
        request.leaderId());
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0L : id;
  }

  public void update(long id, AccountUpdateRequest request, Long leaderId) {
    jdbcTemplate.update("""
        UPDATE accounts
        SET display_name = ?,
            role = ?,
            leader_id = ?,
            is_enabled = ?,
            updated_at = NOW()
        WHERE id = ?
        """,
        request.displayName().trim(),
        request.role().name(),
        leaderId,
        Boolean.TRUE.equals(request.isEnabled()) ? 1 : 0,
        id);
  }

  public void toggle(long id, boolean enabled) {
    jdbcTemplate.update("UPDATE accounts SET is_enabled = ?, updated_at = NOW() WHERE id = ?", enabled ? 1 : 0, id);
  }

  public void resetPassword(long id, String passwordHash) {
    jdbcTemplate.update("UPDATE accounts SET password_hash = ?, updated_at = NOW() WHERE id = ?", passwordHash, id);
  }

  public int delete(long id) {
    return jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", id);
  }

  public int enabledKeeperCount(long leaderId) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM accounts WHERE leader_id = ? AND role = 'KEEPER' AND is_enabled = 1",
        Integer.class,
        leaderId);
    return count == null ? 0 : count;
  }

  public void updateLastLogin(String phone) {
    jdbcTemplate.update("UPDATE accounts SET last_login_at = NOW(), updated_at = NOW() WHERE phone = ? OR username = ?", phone, phone);
  }

  private String where(Role role, String keyword, Boolean enabled, List<Object> args) {
    StringBuilder sql = new StringBuilder(" WHERE 1=1 ");
    if (role != null) {
      sql.append(" AND a.role = ? ");
      args.add(role.name());
    }
    if (enabled != null) {
      sql.append(" AND a.is_enabled = ? ");
      args.add(enabled ? 1 : 0);
    }
    if (keyword != null && !keyword.isBlank()) {
      String like = "%" + keyword.trim() + "%";
      sql.append(" AND (COALESCE(a.phone, a.username) LIKE ? OR a.display_name LIKE ?) ");
      args.add(like);
      args.add(like);
    }
    return sql.toString();
  }

  private AccountAdminItem map(ResultSet rs, int rowNum) throws SQLException {
    Role role = Role.valueOf(rs.getString("role"));
    return new AccountAdminItem(
        rs.getLong("id"),
        rs.getString("phone"),
        rs.getString("display_name"),
        role,
        roleLabel(role),
        rs.getObject("leader_id", Long.class),
        rs.getString("leader_name"),
        rs.getInt("is_enabled") == 1,
        rs.getTimestamp("last_login_at") == null ? null : rs.getTimestamp("last_login_at").toLocalDateTime(),
        rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());
  }

  private String roleLabel(Role role) {
    return switch (role) {
      case ADMIN -> "\u7ba1\u7406\u5458";
      case LEADER -> "\u7ec4\u957f";
      case KEEPER -> "\u7ba1\u5bb6";
    };
  }
}
