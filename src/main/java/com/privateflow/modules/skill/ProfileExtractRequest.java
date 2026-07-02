package com.privateflow.modules.skill;

import java.util.List;
import java.util.Map;

public record ProfileExtractRequest(
    String conversationText,
    Map<String, Object> existingProfile,
    List<String> targetFields,
    String caller
) {
}
