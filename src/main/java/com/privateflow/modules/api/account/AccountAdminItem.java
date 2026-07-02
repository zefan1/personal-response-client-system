package com.privateflow.modules.api.account;

import com.privateflow.modules.api.Role;
import java.time.LocalDateTime;

public record AccountAdminItem(
    long id,
    String phone,
    String displayName,
    Role role,
    String roleLabel,
    Long leaderId,
    String leaderName,
    boolean isEnabled,
    LocalDateTime lastLoginAt,
    LocalDateTime createdAt
) {
}
