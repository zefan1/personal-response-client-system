package com.privateflow.modules.customer.admin;

public record SmartSheetConnectionResult(
    boolean connected,
    String tableName,
    String documentId,
    String sheetId,
    String viewId,
    String documentUrl) {
}
