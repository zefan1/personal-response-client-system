package com.privateflow.modules.skill;

import java.util.List;
import java.util.Map;

public record SkillRequest(
    Scene scene,
    String leadType,
    String phone,
    String clientMessage,
    Map<String, Object> customer,
    Map<String, Object> systemPrompt,
    List<String> previousSuggestions,
    List<Map<String, String>> chatContext,
    String caller
) {
}
