package com.privateflow.modules.customer.sync;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mock-externals", havingValue = "false", matchIfMissing = true)
public class UnavailableSheetClient implements SheetClient {

  @Override
  public List<SheetRow> fetchIncrementalRows(String sourceTable, LocalDateTime modifiedAfter, int limit) {
    throw new IllegalStateException("企微智能表格真实客户端尚未配置；开发环境请设置 MOCK_EXTERNALS=true");
  }
}
