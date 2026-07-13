package com.privateflow.modules.customer.sync;

public record SheetSource(
    Long datasourceId,
    String sheetId,
    String sourceTable
) {
}
