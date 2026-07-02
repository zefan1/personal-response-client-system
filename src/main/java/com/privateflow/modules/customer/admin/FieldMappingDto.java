package com.privateflow.modules.customer.admin;

public record FieldMappingDto(
    Long id,
    String sourceField,
    String targetField,
    boolean enabled
) {
}
