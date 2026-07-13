package com.privateflow.modules.quicksearch.admin;

import com.privateflow.modules.quicksearch.ContentType;

public record QuickSearchAdminListQuery(
    ContentType contentType,
    String leadType,
    Boolean enabled,
    String keyword,
    int page,
    int size,
    String sortBy,
    String sortDir
) {
}
