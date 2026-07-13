package com.privateflow.modules.customer.sync;

import java.time.LocalDateTime;
import java.util.List;

public interface SheetClient {
  List<SheetRow> fetchIncrementalRows(SheetSource source, LocalDateTime modifiedAfter, int limit);
}
