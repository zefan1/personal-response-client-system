package com.privateflow.modules.profile.service;

import com.privateflow.modules.skill.FieldUpdate;
import java.util.Map;

public record RoutedProfileUpdates(
    Map<String, FieldUpdate> high,
    Map<String, FieldUpdate> medium
) {
}
