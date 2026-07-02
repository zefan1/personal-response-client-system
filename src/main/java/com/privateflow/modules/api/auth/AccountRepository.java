package com.privateflow.modules.api.auth;

import com.privateflow.modules.api.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {

  private final JdbcTemplate jdbcTemplate;

  public AccountRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<Account> findEnabledByUsername(String username) {
    return findByPhone(username).filter(Account::enabled);
  }

  public Optional<Account> findByPhone(String phone) {
    List<Account> rows = jdbcTemplate.query("""
        SELECT id, COALESCE(phone, username) AS phone, password_hash, display_name, role, leader_id, is_enabled
        FROM accounts
        WHERE (phone = ? OR username = ?)
        LIMIT 1
        """, (rs, rowNum) -> new Account(
        rs.getLong("id"),
        rs.getString("phone"),
        rs.getString("password_hash"),
        rs.getString("display_name"),
        Role.valueOf(rs.getString("role")),
        rs.getObject("leader_id", Long.class),
        rs.getInt("is_enabled") == 1), phone, phone);
    return rows.stream().findFirst();
  }

  public Optional<Account> findById(long id) {
    List<Account> rows = jdbcTemplate.query("""
        SELECT id, COALESCE(phone, username) AS phone, password_hash, display_name, role, leader_id, is_enabled
        FROM accounts
        WHERE id = ? AND is_enabled = 1
        LIMIT 1
        """, (rs, rowNum) -> new Account(
        rs.getLong("id"),
        rs.getString("phone"),
        rs.getString("password_hash"),
        rs.getString("display_name"),
        Role.valueOf(rs.getString("role")),
        rs.getObject("leader_id", Long.class),
        rs.getInt("is_enabled") == 1), id);
    return rows.stream().findFirst();
  }

  public List<Account> findEnabledByRole(Role role) {
    return jdbcTemplate.query("""
        SELECT id, COALESCE(phone, username) AS phone, password_hash, display_name, role, leader_id, is_enabled
        FROM accounts
        WHERE role = ? AND is_enabled = 1
        ORDER BY id ASC
        """, (rs, rowNum) -> new Account(
        rs.getLong("id"),
        rs.getString("phone"),
        rs.getString("password_hash"),
        rs.getString("display_name"),
        Role.valueOf(rs.getString("role")),
        rs.getObject("leader_id", Long.class),
        rs.getInt("is_enabled") == 1), role.name());
  }

  public void updateLastLogin(String phone) {
    jdbcTemplate.update("UPDATE accounts SET last_login_at = NOW(), updated_at = NOW() WHERE phone = ? OR username = ?", phone, phone);
  }
}
