package com.privateflow.modules.api.auth;

import com.privateflow.modules.api.Role;
import java.util.Set;

public record AuthUser(
    String phone,
    String displayName,
    Role role,
    Long leaderId,
    long tokenVersion,
    Set<String> permissions
) {

  public AuthUser {
    permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
  }

  public AuthUser(String phone, String displayName, Role role, Long leaderId) {
    this(phone, displayName, role, leaderId, 0L, Set.of());
  }

  public AuthUser(String phone, String displayName, Role role, Long leaderId, long tokenVersion) {
    this(phone, displayName, role, leaderId, tokenVersion, Set.of());
  }

  public String username() {
    return phone;
  }
}
