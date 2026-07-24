package com.privateflow.modules.templates;

import java.time.LocalDateTime;

public record TeamTemplate(
    long quickSearchItemId,
    long promotionCandidateId,
    String title,
    String body,
    String shortcutCode,
    TemplateMetadata metadata,
    LocalDateTime publishedAt
) {
}
