package com.privateflow.modules.api.auth;

import com.privateflow.modules.api.Role;

public record AuthUser(String phone, String displayName, Role role, Long leaderId) {
  public String username() {
    return phone;
  }
}
