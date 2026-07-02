package com.privateflow.modules.tablewrite.client;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mock-externals", havingValue = "true")
public class MockWecomTableClient implements WecomTableClient {

  @Override
  public String createRow(String sourceTable, Map<String, Object> fields, Duration timeout) {
    return "mock-row-" + UUID.randomUUID();
  }

  @Override
  public void updateRow(String sourceTable, String sourceRowId, Map<String, Object> fields, Duration timeout) {
  }
}
