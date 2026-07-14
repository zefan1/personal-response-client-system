package com.privateflow.modules.api.auth;

import com.privateflow.modules.api.Role;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountPermissionRepository {

  private final JdbcTemplate jdbcTemplate;

  public AccountPermissionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Set<String> findEnabledByAccountId(long accountId) {
    return new LinkedHashSet<>(jdbcTemplate.queryForList("""
        SELECT permission_code
        FROM account_permissions
        WHERE account_id = ? AND is_enabled = 1
        ORDER BY permission_code
        """, String.class, accountId));
  }

  public Set<String> findEnabledByPhone(String phone) {
    return new LinkedHashSet<>(jdbcTemplate.queryForList("""
        SELECT p.permission_code
        FROM account_permissions p
        JOIN accounts a ON a.id = p.account_id
        WHERE (a.phone = ? OR a.username = ?) AND a.is_enabled = 1 AND p.is_enabled = 1
        ORDER BY p.permission_code
        """, String.class, phone, phone));
  }

  public boolean hasPermission(String phone, Role role, String permissionCode) {
    if (role == Role.ADMIN) {
      return true;
    }
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM account_permissions p
        JOIN accounts a ON a.id = p.account_id
        WHERE (a.phone = ? OR a.username = ?)
          AND a.is_enabled = 1
          AND p.permission_code = ?
          AND p.is_enabled = 1
        """, Integer.class, phone, phone, permissionCode);
    return count != null && count > 0;
  }

  public void replace(long accountId, Collection<String> permissions, String grantedBy) {
    Set<String> normalized = permissions == null ? Set.of() : new LinkedHashSet<>(permissions);
    jdbcTemplate.update("DELETE FROM account_permissions WHERE account_id = ?", accountId);
    for (String permission : normalized) {
      jdbcTemplate.update("""
          INSERT INTO account_permissions (account_id, permission_code, is_enabled, granted_by)
          VALUES (?, ?, 1, ?)
          """, accountId, permission, grantedBy);
    }
  }

  public List<Long> accountIdsWithPermission(String permissionCode) {
    return jdbcTemplate.queryForList("""
        SELECT account_id
        FROM account_permissions
        WHERE permission_code = ? AND is_enabled = 1
        """, Long.class, permissionCode);
  }
}
