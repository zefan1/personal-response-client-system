package com.privateflow.modules.tags;

public record TagCategoryRequest(
    String categoryName,
    String boundField,
    Boolean isEnabled,
    Integer sortOrder
) {
}
