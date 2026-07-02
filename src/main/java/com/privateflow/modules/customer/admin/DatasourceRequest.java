package com.privateflow.modules.customer.admin;

public record DatasourceRequest(
    String name,
    String sheetId,
    String sourceTable,
    String description
) {
}
