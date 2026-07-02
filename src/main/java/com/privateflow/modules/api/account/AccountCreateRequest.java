package com.privateflow.modules.api.account;

import com.privateflow.modules.api.Role;

public record AccountCreateRequest(
    String phone,
    String password,
    String displayName,
    Role role,
    Long leaderId
) {
}
