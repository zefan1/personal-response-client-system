package com.privateflow.modules.tablewrite.client;

import com.privateflow.modules.customer.sync.SheetClient;
import com.privateflow.modules.customer.sync.SheetRow;
import com.privateflow.modules.customer.sync.SheetSource;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mock-externals", havingValue = "false", matchIfMissing = true)
public class HttpWecomTableClient implements WecomTableClient, SheetClient {

  private final WecomSmartSheetRecordClient recordClient;
  private final TableConfigProvider configProvider;

  public HttpWecomTableClient(
      WecomSmartSheetRecordClient recordClient, TableConfigProvider configProvider) {
    this.recordClient = recordClient;
    this.configProvider = configProvider;
  }

  @Override
  public List<SheetRow> fetchIncrementalRows(SheetSource source, LocalDateTime modifiedAfter, int limit) {
    Duration timeout = Duration.ofMillis(configProvider.get().writeTimeoutMs());
    return recordClient.fetchIncrementalRows(source, modifiedAfter, limit, timeout);
  }

  @Override
  public String createRow(String sourceTable, Map<String, Object> fields, Duration timeout) {
    return recordClient.createRow(sourceTable, fields, timeout);
  }

  @Override
  public void updateRow(String sourceTable, String sourceRowId, Map<String, Object> fields, Duration timeout) {
    recordClient.updateRow(sourceTable, sourceRowId, fields, timeout);
  }
}
