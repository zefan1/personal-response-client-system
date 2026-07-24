package com.privateflow.modules.templates;

public record PersonalTemplateRequest(
    String title,
    String body,
    String originalAiReply,
    TemplateMetadata metadata,
    String sourceReplySessionId
) {
}
