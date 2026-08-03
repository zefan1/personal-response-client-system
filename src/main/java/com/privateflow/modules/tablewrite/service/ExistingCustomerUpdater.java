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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ExistingCustomerUpdater {

  private static final Logger log = LoggerFactory.getLogger(ExistingCustomerUpdater.class);

  private final WecomTableClient tableClient;
  private final TableConfigProvider configProvider;
  private final TableFieldMappingResolver mappingResolver;
  private final TagExchangeService exchangeService;
  private final ProfileProjectionFieldFilter profileProjectionFieldFilter;

  @Autowired
  public ExistingCustomerUpdater(
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      TableFieldMappingResolver mappingResolver,
      TagExchangeService exchangeService,
      ProfileProjectionFieldFilter profileProjectionFieldFilter) {
    this.tableClient = tableClient;
    this.configProvider = configProvider;
    this.mappingResolver = mappingResolver;
    this.exchangeService = exchangeService;
    this.profileProjectionFieldFilter = profileProjectionFieldFilter;
  }

  public ExistingCustomerUpdater(
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      TableFieldMappingResolver mappingResolver,
      TagExchangeService exchangeService) {
    this(tableClient, configProvider, mappingResolver, exchangeService, null);
  }

  public ExistingCustomerUpdater(
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      TableFieldMappingResolver mappingResolver) {
    this(tableClient, configProvider, mappingResolver, null, null);
  }

  public void update(Customer customer, CustomerMessageSentEvent event) {
    updateFields(customer, followupFields(event));
  }

  public void updateFields(Customer customer, Map<String, Object> requestedFields) {
    if (blank(customer.getSourceTable()) || blank(customer.getSourceRowId())) {
      throw new TableWriteException(TableWriteErrorCodes.TABLE_WRITE_FAILED, "customer source table or row id is missing");
    }
    Map<String, Object> fields = new LinkedHashMap<>();
    if (requestedFields != null) {
      fields.putAll(requestedFields);
    }
    fields.remove("lastFollowupAt");
    TagExchangeResult exchange = exchangeService == null
        ? new TagExchangeResult(fields, java.util.List.of(), java.util.List.of())
        : exchangeService.prepareOutbound(
            TagExchangeSourceType.TABLE_DISPLAY_PROJECTION,
            customer.getSourceRowId(),
            fields);
    Duration timeout = Duration.ofMillis(configProvider.get().writeTimeoutMs());
    Map<String, Object> sourceFields;
    if (profileProjectionFieldFilter == null) {
      sourceFields = mappingResolver.toSourceFields(customer.getSourceTable(), exchange.acceptedFields());
    } else {
      ProfileProjectionFieldFilter.Result filtered = profileProjectionFieldFilter.filter(
          customer.getSourceTable(), exchange.acceptedFields(), timeout);
      sourceFields = filtered.fields();
      if (!filtered.skippedSelectableFields().isEmpty()) {
        log.warn("skip invalid smart table select values, customerId={}, fields={}",
            customer.getId(), filtered.skippedSelectableFields());
      }
    }
    if (sourceFields.isEmpty()) {
      return;
    }
    tableClient.updateRow(
        customer.getSourceTable(),
        customer.getSourceRowId(),
        sourceFields,
        timeout);
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
}
