package com.privateflow.modules.api.account;

import com.privateflow.modules.api.Role;

public record AccountUpdateRequest(
    String displayName,
    Role role,
    Long leaderId,
    Boolean isEnabled
) {
}
