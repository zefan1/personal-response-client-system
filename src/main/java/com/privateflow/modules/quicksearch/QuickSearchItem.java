package com.privateflow.modules.quicksearch;

import java.time.LocalDateTime;

public record QuickSearchItem(
    Long id,
    ContentType contentType,
    String scene,
    String leadType,
    String title,
    String shortcutCode,
    String content,
    String imageUrl,
    int sortOrder,
    boolean isEnabled,
    LocalDateTime updatedAt
) {
}
