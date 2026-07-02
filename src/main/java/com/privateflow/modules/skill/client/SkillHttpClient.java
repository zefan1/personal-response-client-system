package com.privateflow.modules.skill.client;

import java.util.Map;

public interface SkillHttpClient {
  String call(Map<String, Object> payload, int timeoutMs);
}
