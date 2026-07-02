package com.privateflow.modules.tags;

public record TagValueRequest(
    Long categoryId,
    String tagValue,
    String displayName,
    Boolean isEnabled,
    Integer sortOrder
) {
}
