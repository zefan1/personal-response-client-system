package com.privateflow.modules.profile.service;

import com.privateflow.modules.profile.config.ProfileConfigProvider;
import com.privateflow.modules.profile.infra.ProfileFieldRegistry;
import com.privateflow.modules.skill.FieldUpdate;
import com.privateflow.modules.skill.ProfileUpdates;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ConfidenceRouter {

  private final ProfileConfigProvider configProvider;
  private final ProfileFieldRegistry fieldRegistry;

  public ConfidenceRouter(ProfileConfigProvider configProvider, ProfileFieldRegistry fieldRegistry) {
    this.configProvider = configProvider;
    this.fieldRegistry = fieldRegistry;
  }

  public RoutedProfileUpdates route(ProfileUpdates updates) {
    Map<String, FieldUpdate> high = new LinkedHashMap<>();
    Map<String, FieldUpdate> medium = new LinkedHashMap<>();
    if (updates == null || updates.fields() == null || updates.fields().isEmpty()) {
      return new RoutedProfileUpdates(high, medium);
    }
    Set<String> targetFields = Set.copyOf(configProvider.get().extractFields());
    updates.fields().forEach((fieldName, update) -> {
      if (!targetFields.contains(fieldName) || !fieldRegistry.supports(fieldName) || update == null) {
        return;
      }
      String confidence = update.confidence() == null ? "" : update.confidence().trim().toUpperCase();
      if ("HIGH".equals(confidence)) {
        high.put(fieldName, update);
      } else if ("MEDIUM".equals(confidence)) {
        medium.put(fieldName, update);
      }
    });
    return new RoutedProfileUpdates(high, medium);
  }
}
