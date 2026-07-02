package com.privateflow.modules.api.auth;

import com.privateflow.modules.api.Role;

public record AuthUser(String username, String displayName, Role role, Long leaderId) {
}
