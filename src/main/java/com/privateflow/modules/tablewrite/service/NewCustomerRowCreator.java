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
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NewCustomerRowCreator {

  private final WecomTableClient tableClient;
  private final TableConfigProvider configProvider;
  private final TableFieldMappingResolver mappingResolver;
  private final CustomerRepository customerRepository;
  private final DatasourceAdminRepository datasourceRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final TagExchangeService exchangeService;

  @Autowired
  public NewCustomerRowCreator(
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      TableFieldMappingResolver mappingResolver,
      CustomerRepository customerRepository,
      DatasourceAdminRepository datasourceRepository,
      ApplicationEventPublisher eventPublisher,
      TagExchangeService exchangeService) {
    this.tableClient = tableClient;
    this.configProvider = configProvider;
    this.mappingResolver = mappingResolver;
    this.customerRepository = customerRepository;
    this.datasourceRepository = datasourceRepository;
    this.eventPublisher = eventPublisher;
    this.exchangeService = exchangeService;
  }

  public NewCustomerRowCreator(
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      TableFieldMappingResolver mappingResolver,
      CustomerRepository customerRepository,
      DatasourceAdminRepository datasourceRepository,
      ApplicationEventPublisher eventPublisher) {
    this(tableClient, configProvider, mappingResolver, customerRepository, datasourceRepository, eventPublisher, null);
  }

  public void create(CustomerMessageSentEvent event) {
    if (event == null || event.phone() == null || event.phone().isBlank()) {
      throw new TableWriteException(
          TableWriteErrorCodes.TABLE_WRITE_BLOCKED,
          "smart table create is blocked because the configured unique field has no phone value");
    }
    createRow(
        event.customerId(),
        event.phone(),
        event.nickname(),
        event.leadType(),
        event.sourceTable(),
        newCustomerFields(event));
  }

  public void create(Customer customer) {
    if (customer == null || customer.getPhone() == null || customer.getPhone().isBlank()) {
      throw new TableWriteException(
          TableWriteErrorCodes.TABLE_WRITE_BLOCKED,
          "smart table create is blocked because the configured unique field has no phone value");
    }
    Map<String, Object> internal = newCustomerFields(customer);
    createRow(
        customer.getId(),
        customer.getPhone(),
        customer.getNickname(),
        customer.getLeadType(),
        customer.getSourceTable(),
        internal);
  }

  public Map<String, Object> newCustomerFields(Customer customer) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("phone", customer.getPhone());
    fields.put("nickname", customer.getNickname());
    fields.entrySet().removeIf(entry -> entry.getValue() == null
        || (entry.getValue() instanceof String value && value.isBlank()));
    return fields;
  }

  private void createRow(
      Long customerId,
      String phone,
      String nickname,
      String leadType,
      String requestedSourceTable,
      Map<String, Object> internal) {
    String sourceTable = resolveSourceTable(requestedSourceTable);
    TagExchangeResult exchange = exchangeService == null
        ? new TagExchangeResult(internal, java.util.List.of(), java.util.List.of())
        : exchangeService.prepareOutbound(TagExchangeSourceType.TABLE_WRITE, null, internal);
    Map<String, Object> sourceFields = mappingResolver.toSourceFields(sourceTable, exchange.acceptedFields());
    if (sourceFields.isEmpty()) {
      throw new TableWriteException(TableWriteErrorCodes.TABLE_WRITE_FAILED, "no fields remain after tag validation");
    }
    String rowId = tableClient.createRow(
        sourceTable,
        sourceFields,
        Duration.ofMillis(configProvider.get().writeTimeoutMs()));
    if (customerId != null && customerId > 0) {
      customerRepository.linkTableRow(customerId, sourceTable, rowId);
      eventPublisher.publishEvent(new ProfileUpdatedEvent(phone, List.copyOf(internal.keySet())));
      return;
    }
    Customer customer = new Customer();
    customer.setPhone(phone);
    customer.setNickname(nickname);
    customer.setLeadType(LeadTypes.normalize(leadType));
    applyAnalysisFields(customer, internal);
    customer.setSourceTable(sourceTable);
    customer.setSourceRowId(rowId);
    customer.setSyncedAt(LocalDateTime.now());
    if (exchangeService == null) {
      customerRepository.upsert(customer);
    } else {
      customerRepository.upsert(customer, exchange, TagExchangeSourceType.TABLE_WRITE, rowId);
    }
    eventPublisher.publishEvent(new ProfileUpdatedEvent(phone, List.copyOf(internal.keySet())));
  }

  public Map<String, Object> newCustomerFields(CustomerMessageSentEvent event) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("phone", event.phone());
    fields.put("nickname", event.nickname());
    fields.put("leadType", LeadTypes.normalize(event.leadType()));
    fields.entrySet().removeIf(entry -> entry.getValue() == null
        || (entry.getValue() instanceof String value && value.isBlank()));
    fields.put("customerStage", "待联系");
    if (event.followupFields() != null && !event.followupFields().isEmpty()) {
      fields.putAll(event.followupFields());
      fields.remove("lastFollowupAt");
      return fields;
    }
    if (event.conversationSummary() != null && !event.conversationSummary().isBlank()) {
      fields.put("followupNotes", event.conversationSummary());
    }
    fields.put("nextFollowupDir", event.selectedDirection());
    if (event.followupSuggest() != null) {
      fields.put("nextFollowupDir", event.followupSuggest().nextFollowupDir());
      fields.put("nextFollowupAt", event.followupSuggest().nextFollowupAt());
    }
    return fields;
  }

  public void insertCustomerAfterQueuedCreate(String phone, String sourceTable, String rowId, Map<String, Object> fields) {
    insertCustomerAfterQueuedCreate(null, phone, sourceTable, rowId, fields);
  }

  public void insertCustomerAfterQueuedCreate(
      Long customerId,
      String phone,
      String sourceTable,
      String rowId,
      Map<String, Object> fields) {
    if (customerId != null && customerId > 0) {
      customerRepository.linkTableRow(customerId, sourceTable, rowId);
      eventPublisher.publishEvent(new ProfileUpdatedEvent(phone, List.copyOf(fields.keySet())));
      return;
    }
    Customer customer = new Customer();
    customer.setPhone(phone);
    customer.setNickname(asString(fields.get("nickname")));
    customer.setLeadType(LeadTypes.normalize(asString(fields.get("leadType"))));
    applyAnalysisFields(customer, fields);
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

  private void applyAnalysisFields(Customer customer, Map<String, Object> fields) {
    customer.setCustomerStage(asString(fields.getOrDefault("customerStage", "待联系")));
    customer.setBodyConcerns(asString(fields.get("bodyConcerns")));
    customer.setInternalNote(asString(fields.get("internalNote")));
    customer.setCustomerProfileSummary(asString(fields.get("customerProfileSummary")));
    customer.setFollowupNotes(asString(fields.get("followupNotes")));
    customer.setNextFollowupAt(asDateTime(fields.get("nextFollowupAt")));
    customer.setNextFollowupDir(asString(fields.get("nextFollowupDir")));
    customer.setFirstTrackingCapture(asString(fields.get("firstTrackingCapture")));
    customer.setSecondTrackingCapture(asString(fields.get("secondTrackingCapture")));
    customer.setThirdTrackingCapture(asString(fields.get("thirdTrackingCapture")));
  }

  private LocalDateTime asDateTime(Object value) {
    String text = asString(value);
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return LocalDateTime.parse(text.trim().replace(" ", "T"));
    } catch (RuntimeException ex) {
      return null;
    }
  }
}
