package com.privateflow.modules.tablewrite.service;

import com.privateflow.modules.tablewrite.ManualSaveRequest;
import com.privateflow.modules.tablewrite.ManualSaveResult;
import com.privateflow.modules.tablewrite.TableWriteErrorCodes;
import com.privateflow.modules.tablewrite.TableWriteException;
import com.privateflow.modules.tablewrite.client.WecomTableClient;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ManualSaveHandler {

  private final WecomTableClient tableClient;
  private final TableConfigProvider configProvider;

  public ManualSaveHandler(WecomTableClient tableClient, TableConfigProvider configProvider) {
    this.tableClient = tableClient;
    this.configProvider = configProvider;
  }

  public ManualSaveResult save(String phone, ManualSaveRequest request) {
    if (phone == null || phone.isBlank()
        || request == null
        || blank(request.sourceTable())
        || blank(request.sourceRowId())
        || request.fields() == null
        || request.fields().isEmpty()) {
      throw new TableWriteException(TableWriteErrorCodes.BAD_REQUEST, "sourceTable, sourceRowId and fields are required");
    }
    try {
      tableClient.updateRow(
          request.sourceTable(),
          request.sourceRowId(),
          request.fields(),
          Duration.ofMillis(configProvider.get().writeTimeoutMs()));
      return new ManualSaveResult(true, new ArrayList<>(request.fields().keySet()));
    } catch (RuntimeException ex) {
      throw new TableWriteException(TableWriteErrorCodes.TABLE_WRITE_FAILED, "table write failed: " + ex.getMessage());
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
