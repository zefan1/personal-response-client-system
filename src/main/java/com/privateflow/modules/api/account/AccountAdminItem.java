package com.privateflow.modules.api.account;

import com.privateflow.modules.api.Role;
import java.time.LocalDateTime;
import java.util.Set;

public record AccountAdminItem(
    long id,
    String phone,
    String displayName,
    Role role,
    String roleLabel,
    Long leaderId,
    String leaderName,
    boolean isEnabled,
    Set<String> permissions,
    LocalDateTime lastLoginAt,
    LocalDateTime createdAt
) {

  public AccountAdminItem(
      long id,
      String phone,
      String displayName,
      Role role,
      String roleLabel,
      Long leaderId,
      String leaderName,
      boolean isEnabled,
      LocalDateTime lastLoginAt,
      LocalDateTime createdAt) {
    this(id, phone, displayName, role, roleLabel, leaderId, leaderName, isEnabled, Set.of(), lastLoginAt, createdAt);
  }
}
