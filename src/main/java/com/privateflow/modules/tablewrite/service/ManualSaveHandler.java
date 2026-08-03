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
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ManualSaveHandler {

  private static final Logger log = LoggerFactory.getLogger(ManualSaveHandler.class);

  private final WecomTableClient tableClient;
  private final TableConfigProvider configProvider;
  private final CustomerQueryService customerQueryService;
  private final CustomerAccessService customerAccessService;
  private final TableFieldMappingResolver mappingResolver;
  private final TagExchangeService exchangeService;

  @Autowired
  public ManualSaveHandler(
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      CustomerQueryService customerQueryService,
      CustomerAccessService customerAccessService,
      TableFieldMappingResolver mappingResolver,
      TagExchangeService exchangeService) {
    this.tableClient = tableClient;
    this.configProvider = configProvider;
    this.customerQueryService = customerQueryService;
    this.customerAccessService = customerAccessService;
    this.mappingResolver = mappingResolver;
    this.exchangeService = exchangeService;
  }

  public ManualSaveHandler(
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      CustomerQueryService customerQueryService,
      CustomerAccessService customerAccessService) {
    this(tableClient, configProvider, customerQueryService, customerAccessService, null, null);
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
    String serverTable = customer.getSourceTable();
    String serverRowId = customer.getSourceRowId();
    if (blank(serverTable) || blank(serverRowId)) {
      throw new TableWriteException(TableWriteErrorCodes.BAD_REQUEST, "source reference is invalid");
    }
    serverTable = serverTable.trim();
    serverRowId = serverRowId.trim();
    if (!serverTable.equals(request.sourceTable().trim())
        || !serverRowId.equals(request.sourceRowId().trim())) {
      throw new TableWriteException(TableWriteErrorCodes.BAD_REQUEST, "source reference is invalid");
    }
    try {
      Map<String, Object> internalFields = mappingResolver == null
          ? Map.of()
          : mappingResolver.toInternalFields(serverTable, request.fields());
      TagExchangeResult exchange = exchangeService == null
          ? new TagExchangeResult(internalFields, java.util.List.of(), java.util.List.of())
          : exchangeService.prepareOutbound(
              TagExchangeSourceType.TABLE_WRITE,
              serverRowId,
              internalFields);
      Map<String, Object> fields = mappingResolver == null
          ? request.fields()
          : mappingResolver.mergeSourceFields(
              serverTable,
              request.fields(),
              exchange.acceptedFields(),
              exchange.filteredFields());
      if (fields.isEmpty()) {
        return new ManualSaveResult(false, java.util.List.of(), exchange.filteredFields(), exchange.unmatched().size());
      }
      tableClient.updateRow(
          serverTable,
          serverRowId,
          fields,
          Duration.ofMillis(configProvider.get().writeTimeoutMs()));
      return new ManualSaveResult(
          true,
          new ArrayList<>(fields.keySet()),
          exchange.filteredFields(),
          exchange.unmatched().size());
    } catch (RuntimeException failure) {
      log.warn(
          "manual table save failed for configured source reference, failureType={}",
          failure.getClass().getSimpleName());
      throw new TableWriteException(TableWriteErrorCodes.TABLE_WRITE_FAILED, "table write failed");
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
