package com.privateflow.modules.templates;

import java.time.LocalDateTime;

public record PersonalTemplate(
    long id,
    String title,
    String body,
    TemplateMetadata metadata,
    String sourceReplySessionId,
    long usageCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
