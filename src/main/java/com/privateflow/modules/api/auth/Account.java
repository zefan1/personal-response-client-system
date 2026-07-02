package com.privateflow.modules.api.auth;

import com.privateflow.modules.api.Role;

public record Account(
    Long id,
    String phone,
    String passwordHash,
    String displayName,
    Role role,
    Long leaderId,
    boolean enabled
) {
  public String username() {
    return phone;
  }
}
