package com.privateflow.modules.customer.admin;

import java.util.List;

public record IntentProjectMappingSaveRequest(
    List<String> keywords,
    Integer priority,
    Boolean enabled) {
}
