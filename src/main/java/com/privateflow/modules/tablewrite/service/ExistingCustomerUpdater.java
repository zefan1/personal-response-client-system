package com.privateflow.modules.tablewrite.service;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.tablewrite.TableWriteException;
import com.privateflow.modules.tablewrite.TableWriteErrorCodes;
import com.privateflow.modules.tablewrite.client.WecomTableClient;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ExistingCustomerUpdater {

  private final WecomTableClient tableClient;
  private final TableConfigProvider configProvider;
  private final TableFieldMappingResolver mappingResolver;

  public ExistingCustomerUpdater(
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      TableFieldMappingResolver mappingResolver) {
    this.tableClient = tableClient;
    this.configProvider = configProvider;
    this.mappingResolver = mappingResolver;
  }

  public void update(Customer customer, CustomerMessageSentEvent event) {
    if (blank(customer.getSourceTable()) || blank(customer.getSourceRowId())) {
      throw new TableWriteException(TableWriteErrorCodes.TABLE_WRITE_FAILED, "customer source table or row id is missing");
    }
    Map<String, Object> fields = followupFields(event);
    tableClient.updateRow(
        customer.getSourceTable(),
        customer.getSourceRowId(),
        mappingResolver.toSourceFields(customer.getSourceTable(), fields),
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
