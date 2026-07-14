package com.privateflow.modules.api.account;

import com.privateflow.modules.api.Role;
import java.util.Set;

public record AccountCreateRequest(
    String phone,
    String password,
    String displayName,
    Role role,
    Long leaderId,
    Set<String> permissions
) {

  public AccountCreateRequest(String phone, String password, String displayName, Role role, Long leaderId) {
    this(phone, password, displayName, role, leaderId, null);
  }
}
