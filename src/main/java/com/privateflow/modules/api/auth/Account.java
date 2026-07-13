package com.privateflow.modules.api.auth;

import com.privateflow.modules.api.Role;

public record Account(
    Long id,
    String phone,
    String passwordHash,
    String displayName,
    Role role,
    Long leaderId,
    boolean enabled,
    long tokenVersion
) {
  public Account(Long id, String phone, String passwordHash, String displayName, Role role, Long leaderId, boolean enabled) {
    this(id, phone, passwordHash, displayName, role, leaderId, enabled, 0L);
  }

  public String username() {
    return phone;
  }
}
