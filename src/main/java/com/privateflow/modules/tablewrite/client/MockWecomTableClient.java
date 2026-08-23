package com.privateflow.modules.tablewrite.client;

import java.time.Duration;
import java.util.Map;
import com.privateflow.modules.tablewrite.TableWriteErrorCodes;
import com.privateflow.modules.tablewrite.TableWriteException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mock-externals", havingValue = "true")
public class MockWecomTableClient implements WecomTableClient {

  @Override
  public String createRow(String sourceTable, Map<String, Object> fields, Duration timeout) {
    throw blocked();
  }

  @Override
  public void updateRow(String sourceTable, String sourceRowId, Map<String, Object> fields, Duration timeout) {
    throw blocked();
  }

  private TableWriteException blocked() {
    return new TableWriteException(
        TableWriteErrorCodes.TABLE_WRITE_BLOCKED,
        "当前为模拟外部服务，未写入企业微信客户表");
  }
}
