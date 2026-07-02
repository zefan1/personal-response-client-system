package com.privateflow.modules.analytics;

import com.privateflow.modules.api.Role;

public record AnalyticsScope(Role role, String username, String caller) {
}
