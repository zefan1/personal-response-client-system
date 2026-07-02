package com.privateflow.modules.tablewrite.client;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mock-externals", havingValue = "false", matchIfMissing = true)
public class UnavailableWecomTableClient implements WecomTableClient {

  @Override
  public String createRow(String sourceTable, Map<String, Object> fields, Duration timeout) {
    throw new IllegalStateException("WeCom smart table client is not configured; set MOCK_EXTERNALS=true for development");
  }

  @Override
  public void updateRow(String sourceTable, String sourceRowId, Map<String, Object> fields, Duration timeout) {
    throw new IllegalStateException("WeCom smart table client is not configured; set MOCK_EXTERNALS=true for development");
  }
}
