package com.privateflow.modules.customer.sync;

import java.time.LocalDateTime;
import java.util.List;

public interface SheetClient {
  List<SheetRow> fetchIncrementalRows(String sourceTable, LocalDateTime modifiedAfter, int limit);
}
