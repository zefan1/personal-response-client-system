package com.privateflow.modules.customer.admin;

import java.util.List;

public record CsvImportResult(
    int totalRows,
    int created,
    int updated,
    int skipped,
    List<RowError> errors
) {
  public record RowError(int row, String reason) {
  }
}
