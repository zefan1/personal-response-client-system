package com.privateflow.modules.quicksearch.admin;

import com.privateflow.modules.quicksearch.ContentType;
import java.time.LocalDateTime;

public record QuickSearchAdminItem(
    Long id,
    ContentType contentType,
    String leadType,
    String title,
    String shortcutCode,
    String content,
    String imageUrl,
    int sortOrder,
    boolean enabled,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
