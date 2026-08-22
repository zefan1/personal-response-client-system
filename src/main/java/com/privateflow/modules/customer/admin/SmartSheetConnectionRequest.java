package com.privateflow.modules.customer.admin;

public record SmartSheetConnectionRequest(
    String documentUrl,
    String role,
    String documentId,
    String sheetId,
    String viewId,
    String uniqueFieldTitle) {

  public SmartSheetConnectionRequest(String documentUrl) {
    this(documentUrl, "PRIMARY", null, null, null, null);
  }

  public SmartSheetConnectionRequest(String documentUrl, String role) {
    this(documentUrl, role, null, null, null, null);
  }
}
