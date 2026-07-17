package com.privateflow.modules.tablewrite;

import java.util.List;

public record ManualSaveResult(
    boolean written,
    List<String> updatedFields,
    List<String> filteredFields,
    int unmatchedCount) {

  public ManualSaveResult(boolean written, List<String> updatedFields) {
    this(written, updatedFields, List.of(), 0);
  }
}
