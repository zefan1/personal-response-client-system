package com.privateflow.modules.tablewrite.service;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.tablewrite.TableWriteException;
import com.privateflow.modules.tablewrite.TableWriteErrorCodes;
import com.privateflow.modules.tablewrite.client.WecomTableClient;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ExistingCustomerUpdater {

  private final WecomTableClient tableClient;
  private final TableConfigProvider configProvider;
  private final TableFieldMappingResolver mappingResolver;
  private final TagExchangeService exchangeService;

  @Autowired
  public ExistingCustomerUpdater(
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      TableFieldMappingResolver mappingResolver,
      TagExchangeService exchangeService) {
    this.tableClient = tableClient;
    this.configProvider = configProvider;
    this.mappingResolver = mappingResolver;
    this.exchangeService = exchangeService;
  }

  public ExistingCustomerUpdater(
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      TableFieldMappingResolver mappingResolver) {
    this(tableClient, configProvider, mappingResolver, null);
  }

  public void update(Customer customer, CustomerMessageSentEvent event) {
    if (blank(customer.getSourceTable()) || blank(customer.getSourceRowId())) {
      throw new TableWriteException(TableWriteErrorCodes.TABLE_WRITE_FAILED, "customer source table or row id is missing");
    }
    Map<String, Object> fields = followupFields(event);
    TagExchangeResult exchange = exchangeService == null
        ? new TagExchangeResult(fields, java.util.List.of(), java.util.List.of())
        : exchangeService.prepareOutbound(
            TagExchangeSourceType.TABLE_WRITE,
            customer.getSourceRowId(),
            fields);
    Map<String, Object> sourceFields = mappingResolver.toSourceFields(customer.getSourceTable(), exchange.acceptedFields());
    if (sourceFields.isEmpty()) {
      return;
    }
    tableClient.updateRow(
        customer.getSourceTable(),
        customer.getSourceRowId(),
        sourceFields,
        Duration.ofMillis(configProvider.get().writeTimeoutMs()));
  }

  public Map<String, Object> followupFields(CustomerMessageSentEvent event) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("followupNotes", event.conversationSummary());
    fields.put("nextFollowupDir", event.selectedDirection());
    if (event.followupSuggest() != null) {
      fields.put("nextFollowupDir", event.followupSuggest().nextFollowupDir());
      fields.put("nextFollowupAt", event.followupSuggest().nextFollowupAt());
    }
    return fields;
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
