package com.privateflow.modules.tablewrite.service;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.common.events.ProfileUpdatedEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.LeadTypes;
import com.privateflow.modules.customer.admin.DatasourceAdminRepository;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.tablewrite.TableWriteErrorCodes;
import com.privateflow.modules.tablewrite.TableWriteException;
import com.privateflow.modules.tablewrite.client.WecomTableClient;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class NewCustomerRowCreator {

  private final WecomTableClient tableClient;
  private final TableConfigProvider configProvider;
  private final TableFieldMappingResolver mappingResolver;
  private final CustomerRepository customerRepository;
  private final DatasourceAdminRepository datasourceRepository;
  private final ApplicationEventPublisher eventPublisher;

  public NewCustomerRowCreator(
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      TableFieldMappingResolver mappingResolver,
      CustomerRepository customerRepository,
      DatasourceAdminRepository datasourceRepository,
      ApplicationEventPublisher eventPublisher) {
    this.tableClient = tableClient;
    this.configProvider = configProvider;
    this.mappingResolver = mappingResolver;
    this.customerRepository = customerRepository;
    this.datasourceRepository = datasourceRepository;
    this.eventPublisher = eventPublisher;
  }

  public void create(CustomerMessageSentEvent event) {
    Map<String, Object> internal = newCustomerFields(event);
    String sourceTable = resolveSourceTable(event.sourceTable());
    Map<String, Object> sourceFields = mappingResolver.toSourceFields(sourceTable, internal);
    String rowId = tableClient.createRow(
        sourceTable,
        sourceFields,
        Duration.ofMillis(configProvider.get().writeTimeoutMs()));
    Customer customer = new Customer();
    customer.setPhone(event.phone());
    customer.setNickname(event.nickname());
    customer.setLeadType(LeadTypes.normalize(event.leadType()));
    customer.setCustomerStage("待联系");
    customer.setFollowupNotes(event.conversationSummary());
    if (event.followupSuggest() != null) {
      customer.setNextFollowupDir(event.followupSuggest().nextFollowupDir());
    } else {
      customer.setNextFollowupDir(event.selectedDirection());
    }
    customer.setSourceTable(sourceTable);
    customer.setSourceRowId(rowId);
    customer.setSyncedAt(LocalDateTime.now());
    customerRepository.upsert(customer);
    eventPublisher.publishEvent(new ProfileUpdatedEvent(event.phone(), List.copyOf(internal.keySet())));
  }

  public Map<String, Object> newCustomerFields(CustomerMessageSentEvent event) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("phone", event.phone());
    fields.put("nickname", event.nickname());
    fields.put("leadType", LeadTypes.normalize(event.leadType()));
    fields.put("customerStage", "待联系");
    fields.put("followupNotes", event.conversationSummary());
    fields.put("nextFollowupDir", event.selectedDirection());
    if (event.followupSuggest() != null) {
      fields.put("nextFollowupDir", event.followupSuggest().nextFollowupDir());
      fields.put("nextFollowupAt", event.followupSuggest().nextFollowupAt());
    }
    return fields;
  }

  public void insertCustomerAfterQueuedCreate(String phone, String sourceTable, String rowId, Map<String, Object> fields) {
    Customer customer = new Customer();
    customer.setPhone(phone);
    customer.setNickname(asString(fields.get("nickname")));
    customer.setLeadType(LeadTypes.normalize(asString(fields.get("leadType"))));
    customer.setCustomerStage(asString(fields.getOrDefault("customerStage", "待联系")));
    customer.setFollowupNotes(asString(fields.get("followupNotes")));
    customer.setNextFollowupDir(asString(fields.get("nextFollowupDir")));
    customer.setSourceTable(sourceTable);
    customer.setSourceRowId(rowId);
    customer.setSyncedAt(LocalDateTime.now());
    customerRepository.upsert(customer);
    eventPublisher.publishEvent(new ProfileUpdatedEvent(phone, List.copyOf(fields.keySet())));
  }

  public String resolveSourceTable(String sourceTable) {
    if (sourceTable != null && !sourceTable.isBlank()) {
      return sourceTable.trim();
    }
    return datasourceRepository.defaultWriteSource()
        .map(source -> source.sourceTable())
        .filter(value -> value != null && !value.isBlank())
        .map(String::trim)
        .orElseThrow(() -> new TableWriteException(
            TableWriteErrorCodes.CONFIG_MISSING,
            "no enabled datasource is configured for new customer table writes"));
  }

  private String asString(Object value) {
    return value == null ? null : value.toString();
  }
}
