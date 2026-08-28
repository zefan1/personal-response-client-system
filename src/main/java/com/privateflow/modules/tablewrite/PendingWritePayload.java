package com.privateflow.modules.tablewrite;

import java.util.Map;

public record PendingWritePayload(
    String sourceTable,
    String sourceRowId,
    Map<String, Object> fields,
    String targetDocumentId,
    String targetSheetId,
    String targetViewId
) {
  public PendingWritePayload(String sourceTable, String sourceRowId, Map<String, Object> fields) {
    this(sourceTable, sourceRowId, fields, "", "", "");
  }

  public boolean hasTargetSnapshot() {
    return targetDocumentId != null && !targetDocumentId.isBlank()
        && targetSheetId != null && !targetSheetId.isBlank()
        && targetViewId != null && !targetViewId.isBlank();
  }
}
