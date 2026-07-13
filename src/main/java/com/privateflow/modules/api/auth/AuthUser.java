package com.privateflow.modules.api.auth;

import com.privateflow.modules.api.Role;

public record AuthUser(String phone, String displayName, Role role, Long leaderId, long tokenVersion) {
  public AuthUser(String phone, String displayName, Role role, Long leaderId) {
    this(phone, displayName, role, leaderId, 0L);
  }

  public String username() {
    return phone;
  }
}
