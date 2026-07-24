package com.privateflow.modules.templates;

import java.time.LocalDateTime;

public record TemplatePromotionCandidate(
    long id,
    long personalTemplateId,
    String ownerUsername,
    String originalAiReply,
    String editedTitle,
    String editedBody,
    TemplateMetadata metadata,
    TemplatePromotionCandidateStatus status,
    String decidedBy,
    LocalDateTime decidedAt,
    LocalDateTime createdAt,
    long personalTemplateUsageCount
) {
}
