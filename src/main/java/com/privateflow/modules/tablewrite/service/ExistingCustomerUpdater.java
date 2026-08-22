package com.privateflow.modules.tablewrite.service;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.tablewrite.TableWriteException;
import com.privateflow.modules.tablewrite.TableWriteErrorCodes;
import com.privateflow.modules.tablewrite.client.WecomTableClient;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ExistingCustomerUpdater {

  private static final Logger log = LoggerFactory.getLogger(ExistingCustomerUpdater.class);
  private static final String INVALID_FIELD_PREFIX = "Invalid value for field: ";

  private final WecomTableClient tableClient;
  private final TableConfigProvider configProvider;
  private final TableFieldMappingResolver mappingResolver;
  private final TagExchangeService exchangeService;
  private final WecomSmartSheetConfig smartSheetConfig;

  @Autowired
  public ExistingCustomerUpdater(
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      TableFieldMappingResolver mappingResolver,
      TagExchangeService exchangeService,
      WecomSmartSheetConfig smartSheetConfig) {
    this.tableClient = tableClient;
    this.configProvider = configProvider;
    this.mappingResolver = mappingResolver;
    this.exchangeService = exchangeService;
    this.smartSheetConfig = smartSheetConfig;
  }

  public ExistingCustomerUpdater(
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      TableFieldMappingResolver mappingResolver) {
    this(tableClient, configProvider, mappingResolver, null, null);
  }

  public ExistingCustomerUpdater(
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      TableFieldMappingResolver mappingResolver,
      TagExchangeService exchangeService) {
    this(tableClient, configProvider, mappingResolver, exchangeService, null);
  }

  public void update(Customer customer, CustomerMessageSentEvent event) {
    updateFields(customer, followupFields(event));
  }

  public void updateFields(Customer customer, Map<String, Object> requestedFields) {
    if (blank(customer.getSourceTable()) || blank(customer.getSourceRowId())) {
      throw new TableWriteException(TableWriteErrorCodes.TABLE_WRITE_FAILED, "customer source table or row id is missing");
    }
    String writeSource = isAssignmentSource(customer.getSourceTable()) && smartSheetConfig != null
        ? smartSheetConfig.sourceTable() : customer.getSourceTable();
    Map<String, Object> fields = new LinkedHashMap<>();
    if (requestedFields != null) {
      fields.putAll(requestedFields);
    }
    if (isAssignmentSource(customer.getSourceTable())) {
      fields.put("phone", customer.getPhone());
    }
    fields.remove("lastFollowupAt");
    TagExchangeResult exchange = exchangeService == null
        ? new TagExchangeResult(fields, java.util.List.of(), java.util.List.of())
        : exchangeService.prepareOutbound(
            TagExchangeSourceType.TABLE_WRITE,
            customer.getSourceRowId(),
            fields);
    Map<String, Object> sourceFields = mappingResolver.toSourceFields(writeSource, exchange.acceptedFields());
    if (sourceFields.isEmpty()) {
      return;
    }
    Duration timeout = Duration.ofMillis(configProvider.get().writeTimeoutMs());
    if (isAssignmentSource(customer.getSourceTable()) && smartSheetConfig != null) {
      updatePrimaryByPhone(writeSource, sourceFields, timeout);
      return;
    }
    tableClient.updateRow(writeSource, customer.getSourceRowId(), sourceFields, timeout);
  }

  private void updatePrimaryByPhone(String sourceTable, Map<String, Object> sourceFields, Duration timeout) {
    String identityField = mappingResolver.sourceFieldFor(sourceTable, "phone");
    while (true) {
      try {
        String primaryRowId = tableClient.createRow(sourceTable, sourceFields, timeout);
        tableClient.updateRow(sourceTable, primaryRowId, sourceFields, timeout);
        return;
      } catch (IllegalArgumentException failure) {
        String invalidField = invalidField(failure.getMessage());
        if (invalidField == null || invalidField.equals(identityField)
            || sourceFields.remove(invalidField) == null) {
          throw failure;
        }
        log.warn("skip incompatible customer-master update field, field={}", invalidField);
      }
    }
  }

  private static String invalidField(String message) {
    if (message == null || !message.startsWith(INVALID_FIELD_PREFIX)) {
      return null;
    }
    String field = message.substring(INVALID_FIELD_PREFIX.length()).trim();
    return field.isEmpty() ? null : field;
  }

  public Map<String, Object> followupFields(CustomerMessageSentEvent event) {
    Map<String, Object> fields = new LinkedHashMap<>();
    if (event.followupFields() != null && !event.followupFields().isEmpty()) {
      fields.putAll(event.followupFields());
      fields.remove("lastFollowupAt");
      return fields;
    }
    if (!blank(event.conversationSummary())) {
      fields.put("followupNotes", event.conversationSummary());
    }
    if (event.followupSuggest() != null && !blank(event.followupSuggest().nextFollowupAt())) {
      fields.put("nextFollowupDir", event.followupSuggest().nextFollowupDir());
      fields.put("nextFollowupAt", event.followupSuggest().nextFollowupAt());
    } else if (event.completeCurrentFollowup()) {
      fields.put("nextFollowupAt", "");
      fields.put("nextFollowupDir", "");
    }
    return fields;
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private boolean isAssignmentSource(String sourceTable) {
    return sourceTable != null && sourceTable.startsWith("ASSIGNMENT:");
  }
}
