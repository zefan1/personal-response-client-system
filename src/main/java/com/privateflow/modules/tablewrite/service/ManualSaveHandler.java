package com.privateflow.modules.tablewrite.service;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
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
  private final CustomerQueryService customerQueryService;
  private final CustomerAccessService customerAccessService;

  public ManualSaveHandler(
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      CustomerQueryService customerQueryService,
      CustomerAccessService customerAccessService) {
    this.tableClient = tableClient;
    this.configProvider = configProvider;
    this.customerQueryService = customerQueryService;
    this.customerAccessService = customerAccessService;
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
    Customer customer = customerQueryService.getByPhone(phone);
    if (customer == null) {
      throw new TableWriteException(TableWriteErrorCodes.BAD_REQUEST, "客户不存在");
    }
    if (!customerAccessService.canAccess(customer)) {
      throw new TableWriteException(TableWriteErrorCodes.BAD_REQUEST, "该客户不在你的负责范围内");
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
