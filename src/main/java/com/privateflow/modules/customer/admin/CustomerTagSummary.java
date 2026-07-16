package com.privateflow.modules.customer.admin;

public record CustomerTagSummary(
    long categoryId,
    String categoryKey,
    String categoryName,
    long valueId,
    String valueCode,
    String displayName) {
}
