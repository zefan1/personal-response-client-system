package com.privateflow.modules.tablewrite.config;

public record AuxiliarySmartSheetTarget(
    String role,
    String documentId,
    String sheetId,
    String viewId,
    String uniqueFieldTitle,
    String documentUrl) {

  public AuxiliarySmartSheetTarget {
    role = text(role);
    documentId = text(documentId);
    sheetId = text(sheetId);
    viewId = text(viewId);
    uniqueFieldTitle = text(uniqueFieldTitle);
    documentUrl = text(documentUrl);
  }

  public AuxiliarySmartSheetTarget(
      String role, String documentId, String sheetId, String viewId, String documentUrl) {
    this(role, documentId, sheetId, viewId,
        "ARRIVAL".equalsIgnoreCase(role) ? "手机号码" : "联系方式", documentUrl);
  }

  public boolean configured() {
    return !documentId.isBlank() && !sheetId.isBlank() && !viewId.isBlank()
        && !uniqueFieldTitle.isBlank();
  }

  private static String text(String value) {
    return value == null ? "" : value.trim();
  }
}
