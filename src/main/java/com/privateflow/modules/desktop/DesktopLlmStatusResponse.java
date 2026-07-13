package com.privateflow.modules.desktop;

public record DesktopLlmStatusResponse(
    DesktopLlmStatus status,
    String label,
    String detail,
    boolean replyGenerationEnabled
) {
}
