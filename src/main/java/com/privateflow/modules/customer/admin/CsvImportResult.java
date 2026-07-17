package com.privateflow.modules.customer.admin;

import java.util.List;

public record CsvImportResult(
    int totalRows,
    int created,
    int updated,
    int skipped,
    List<RowError> errors,
    int unmatchedCount,
    List<Integer> unmatchedRows
) {

  public CsvImportResult(
      int totalRows,
      int created,
      int updated,
      int skipped,
      List<RowError> errors) {
    this(totalRows, created, updated, skipped, errors, 0, List.of());
  }

  public CsvImportResult {
    errors = errors == null ? List.of() : List.copyOf(errors);
    unmatchedRows = unmatchedRows == null ? List.of() : List.copyOf(unmatchedRows);
  }

  public record RowError(int row, String reason) {
  }
}
