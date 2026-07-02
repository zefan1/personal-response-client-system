package com.privateflow.modules.api.ai;

import java.time.LocalDateTime;

public record PromptVersion(
    int version,
    String content,
    String operator,
    boolean stable,
    String changeNote,
    LocalDateTime createdAt
) {
}
