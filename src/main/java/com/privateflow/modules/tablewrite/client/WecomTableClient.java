package com.privateflow.modules.tablewrite.client;

import java.time.Duration;
import java.util.Map;

public interface WecomTableClient {

  String createRow(String sourceTable, Map<String, Object> fields, Duration timeout);

  void updateRow(String sourceTable, String sourceRowId, Map<String, Object> fields, Duration timeout);
}
