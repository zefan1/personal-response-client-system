package com.privateflow.modules.quicksearch.admin;

import com.privateflow.modules.quicksearch.ContentType;

public record QuickSearchItemRequest(
    ContentType contentType,
    String leadType,
    String title,
    String shortcutCode,
    String content,
    String imageUrl,
    Integer sortOrder,
    Boolean enabled,
    String updatedAt
) {
}
