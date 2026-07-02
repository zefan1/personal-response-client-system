package com.privateflow.modules.skill;

import java.util.Map;

public record ProfileUpdates(Map<String, FieldUpdate> fields) {
  public static ProfileUpdates empty() {
    return new ProfileUpdates(Map.of());
  }
}
