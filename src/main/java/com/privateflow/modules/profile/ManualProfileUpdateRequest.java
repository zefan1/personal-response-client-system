package com.privateflow.modules.profile;

import java.util.Map;

public record ManualProfileUpdateRequest(Integer version, Map<String, Object> fields, String operator) {
}
