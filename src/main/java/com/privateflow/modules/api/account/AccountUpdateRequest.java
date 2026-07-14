package com.privateflow.modules.api.account;

import com.privateflow.modules.api.Role;
import java.util.Set;

public record AccountUpdateRequest(
    String displayName,
    Role role,
    Long leaderId,
    Boolean isEnabled,
    Set<String> permissions
) {

  public AccountUpdateRequest(String displayName, Role role, Long leaderId, Boolean isEnabled) {
    this(displayName, role, leaderId, isEnabled, null);
  }
}
