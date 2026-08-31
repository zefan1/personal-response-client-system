package com.privateflow.modules.skill.admin;

import com.privateflow.modules.skill.Suggestion;
import java.util.List;

public record FinalReplyTestResult(
    boolean attempted,
    boolean success,
    long responseTimeMs,
    List<Suggestion> suggestions,
    String message
) {
}
