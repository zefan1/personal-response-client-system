package com.privateflow.modules.customer.admin;

public record SmartSheetConnectionResult(
    boolean connected,
    String role,
    String tableName,
    String documentId,
    String sheetId,
    String viewId,
    String uniqueFieldTitle,
    String documentUrl) {

  public SmartSheetConnectionResult(
      boolean connected,
      String tableName,
      String documentId,
      String sheetId,
      String viewId,
      String documentUrl) {
    this(connected, "PRIMARY", tableName, documentId, sheetId, viewId, "", documentUrl);
  }
}
