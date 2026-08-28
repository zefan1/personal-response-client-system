package com.privateflow.modules.tablewrite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.admin.CustomerStageOptionService;
import com.privateflow.modules.tablewrite.PendingTableWrite;
import com.privateflow.modules.tablewrite.PendingWritePayload;
import com.privateflow.modules.tablewrite.TableWriteActionType;
import com.privateflow.modules.tablewrite.client.AuxiliarySmartSheetWriter;
import com.privateflow.modules.tablewrite.client.WecomTableClient;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import com.privateflow.modules.tablewrite.config.TableConfig;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.PendingTableWriteRepository;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class QueueRetryManager {

  private static final Logger log = LoggerFactory.getLogger(QueueRetryManager.class);
  private final PendingTableWriteRepository repository;
  private final WecomTableClient tableClient;
  private final TableConfigProvider configProvider;
  private final ObjectMapper objectMapper;
  private final CustomerQueryService customerQueryService;
  private final NewCustomerRowCreator newCustomerRowCreator;
  private final TableFieldMappingResolver mappingResolver;
  private final TagExchangeService exchangeService;
  private final AuxiliarySmartSheetWriter auxiliaryWriter;
  private final AuxiliarySmartSheetTargets auxiliaryTargets;
  private final Optional<AuxiliarySmartSheetTarget> assignmentTarget;
  private final Optional<AuxiliarySmartSheetTarget> arrivalTarget;
  private final CustomerMasterProjectionService customerMasterProjectionService;
  private final CustomerStageOptionService stageOptionService;

  @Autowired
  public QueueRetryManager(
      PendingTableWriteRepository repository,
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      ObjectMapper objectMapper,
      CustomerQueryService customerQueryService,
      NewCustomerRowCreator newCustomerRowCreator,
      TableFieldMappingResolver mappingResolver,
      TagExchangeService exchangeService,
      AuxiliarySmartSheetWriter auxiliaryWriter,
      AuxiliarySmartSheetTargets auxiliaryTargets,
      CustomerMasterProjectionService customerMasterProjectionService,
      CustomerStageOptionService stageOptionService) {
    this.repository = repository;
    this.tableClient = tableClient;
    this.configProvider = configProvider;
    this.objectMapper = objectMapper;
    this.customerQueryService = customerQueryService;
    this.newCustomerRowCreator = newCustomerRowCreator;
    this.mappingResolver = mappingResolver;
    this.exchangeService = exchangeService;
    this.auxiliaryWriter = auxiliaryWriter;
    this.auxiliaryTargets = auxiliaryTargets;
    this.assignmentTarget = Optional.empty();
    this.arrivalTarget = Optional.empty();
    this.customerMasterProjectionService = customerMasterProjectionService;
    this.stageOptionService = stageOptionService;
  }

  QueueRetryManager(
      PendingTableWriteRepository repository,
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      ObjectMapper objectMapper,
      CustomerQueryService customerQueryService,
      NewCustomerRowCreator newCustomerRowCreator,
      TableFieldMappingResolver mappingResolver,
      TagExchangeService exchangeService,
      AuxiliarySmartSheetWriter auxiliaryWriter,
      Optional<AuxiliarySmartSheetTarget> assignmentTarget,
      Optional<AuxiliarySmartSheetTarget> arrivalTarget) {
    this(repository, tableClient, configProvider, objectMapper, customerQueryService,
        newCustomerRowCreator, mappingResolver, exchangeService, auxiliaryWriter,
        assignmentTarget, arrivalTarget, null);
  }

  QueueRetryManager(
      PendingTableWriteRepository repository,
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      ObjectMapper objectMapper,
      CustomerQueryService customerQueryService,
      NewCustomerRowCreator newCustomerRowCreator,
      TableFieldMappingResolver mappingResolver,
      TagExchangeService exchangeService,
      AuxiliarySmartSheetWriter auxiliaryWriter,
      Optional<AuxiliarySmartSheetTarget> assignmentTarget,
      Optional<AuxiliarySmartSheetTarget> arrivalTarget,
      CustomerMasterProjectionService customerMasterProjectionService) {
    this.repository = repository;
    this.tableClient = tableClient;
    this.configProvider = configProvider;
    this.objectMapper = objectMapper;
    this.customerQueryService = customerQueryService;
    this.newCustomerRowCreator = newCustomerRowCreator;
    this.mappingResolver = mappingResolver;
    this.exchangeService = exchangeService;
    this.auxiliaryWriter = auxiliaryWriter;
    this.auxiliaryTargets = null;
    this.assignmentTarget = assignmentTarget;
    this.arrivalTarget = arrivalTarget;
    this.customerMasterProjectionService = customerMasterProjectionService;
    this.stageOptionService = null;
  }

  public QueueRetryManager(
      PendingTableWriteRepository repository,
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      ObjectMapper objectMapper,
      CustomerQueryService customerQueryService,
      NewCustomerRowCreator newCustomerRowCreator,
      TableFieldMappingResolver mappingResolver,
      TagExchangeService exchangeService) {
    this(repository, tableClient, configProvider, objectMapper, customerQueryService,
        newCustomerRowCreator, mappingResolver, exchangeService, null,
        Optional.empty(), Optional.empty());
  }

  public QueueRetryManager(
      PendingTableWriteRepository repository,
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      ObjectMapper objectMapper,
      CustomerQueryService customerQueryService,
      NewCustomerRowCreator newCustomerRowCreator) {
    this(repository, tableClient, configProvider, objectMapper, customerQueryService, newCustomerRowCreator, null, null);
  }

  @Scheduled(fixedDelayString = "#{@tableConfigProvider.get().retryIntervalS() * 1000L}")
  public void retryDueWrites() {
    TableConfig config = configProvider.get();
    for (PendingTableWrite item : repository.due(100)) {
      try {
        PendingWritePayload payload = objectMapper.readValue(item.getPayload(), PendingWritePayload.class);
        payload = normalizeStage(payload, item.getCustomerId(), item.getPhone());
        if (retryAuxiliary(payload, item.getCustomerId(), item.getPhone(),
            Duration.ofMillis(config.writeTimeoutMs()))) {
          repository.markResolved(item.getId());
          continue;
        }
        TagExchangeResult exchange = exchangeService == null
            ? new TagExchangeResult(payload.fields(), java.util.List.of(), java.util.List.of())
            : exchangeService.prepareOutbound(
                TagExchangeSourceType.TABLE_WRITE,
                String.valueOf(item.getId()),
                payload.fields());
        if (exchange.acceptedFields().isEmpty()) {
          repository.markResolved(item.getId());
          continue;
        }
        if (item.getActionType() == TableWriteActionType.INSERT) {
          MapPayload remote = remotePayload(payload, exchange);
          if (remote.fields().isEmpty()) {
            repository.markResolved(item.getId());
            continue;
          }
          String rowId = tableClient.createRow(remote.sourceTable(), remote.fields(), Duration.ofMillis(config.writeTimeoutMs()));
          newCustomerRowCreator.insertCustomerAfterQueuedCreate(
              item.getCustomerId(), item.getPhone(), remote.sourceTable(), rowId, exchange.acceptedFields());
        } else {
          PendingWritePayload resolved = resolveExistingRow(item.getCustomerId(), item.getPhone(), payload);
          MapPayload remote = remotePayload(resolved, exchange);
          if (remote.fields().isEmpty()) {
            repository.markResolved(item.getId());
            continue;
          }
          tableClient.updateRow(remote.sourceTable(), remote.sourceRowId(), remote.fields(), Duration.ofMillis(config.writeTimeoutMs()));
        }
        repository.markResolved(item.getId());
      } catch (Exception ex) {
        int nextRetry = item.getRetryCount() + 1;
        if (nextRetry >= config.retryMaxCount()) {
          repository.markFailed(item.getId(), nextRetry, ex.getMessage());
        } else {
          repository.markRetry(item.getId(), nextRetry, LocalDateTime.now().plusSeconds(config.retryIntervalS()), ex.getMessage());
        }
      }
    }
    int staleFailed = repository.countStaleFailed(config.alertFailureHours());
    if (staleFailed > 0) {
      log.warn("table write has {} stale FAILED records, notify target {}", staleFailed, config.alertNotifyTarget());
    }
  }

  private boolean retryAuxiliary(PendingWritePayload payload, Long customerId, String phone, Duration timeout) {
    if ("PRIMARY".equals(payload.sourceTable())) {
      if (customerMasterProjectionService == null) {
        return false;
      }
      Customer customer = customerId != null && customerId > 0
          ? customerQueryService.getById(customerId)
          : customerQueryService.getByPhone(phone);
      if (customer == null) {
        throw new IllegalStateException("PRIMARY retry customer not found");
      }
      customerMasterProjectionService.projectFields(customer, payload.fields());
      return true;
    }
    if ("ASSIGNMENT".equals(payload.sourceTable())) {
      writeAuxiliary(targetForPayload(payload, "ASSIGNMENT"), payload, timeout);
      return true;
    }
    if ("ARRIVAL".equals(payload.sourceTable())) {
      writeAuxiliary(targetForPayload(payload, "ARRIVAL"), payload, timeout);
      return true;
    }
    return false;
  }

  private PendingWritePayload normalizeStage(PendingWritePayload payload, Long customerId, String phone) {
    if (stageOptionService == null || payload == null || payload.fields() == null
        || payload.fields().get("customerStage") == null) {
      return payload;
    }
    Customer customer = customerId != null && customerId > 0
        ? customerQueryService.getById(customerId) : customerQueryService.getByPhone(phone);
    if (customer == null) {
      return payload;
    }
    Map<String, Object> fields = new java.util.LinkedHashMap<>(payload.fields());
    fields.put("customerStage", stageOptionService.normalizeForCustomer(
        customer, String.valueOf(fields.get("customerStage"))));
    return new PendingWritePayload(payload.sourceTable(), payload.sourceRowId(), fields,
        payload.targetDocumentId(), payload.targetSheetId(), payload.targetViewId());
  }

  private Optional<AuxiliarySmartSheetTarget> currentAssignmentTarget() {
    return auxiliaryTargets == null ? assignmentTarget : auxiliaryTargets.assignment();
  }

  private Optional<AuxiliarySmartSheetTarget> currentArrivalTarget() {
    return auxiliaryTargets == null ? arrivalTarget : auxiliaryTargets.arrival();
  }

  private Optional<AuxiliarySmartSheetTarget> targetForPayload(
      PendingWritePayload payload, String role) {
    if (payload != null && payload.hasTargetSnapshot()) {
      String uniqueField = "ARRIVAL".equals(role) ? "手机号码" : "联系方式";
      return Optional.of(new AuxiliarySmartSheetTarget(
          role, payload.targetDocumentId(), payload.targetSheetId(), payload.targetViewId(), uniqueField, ""));
    }
    return "ARRIVAL".equals(role) ? currentArrivalTarget() : currentAssignmentTarget();
  }

  private void writeAuxiliary(
      Optional<AuxiliarySmartSheetTarget> target,
      PendingWritePayload payload,
      Duration timeout) {
    if (auxiliaryWriter == null) {
      throw new IllegalStateException("辅助表写入组件不可用");
    }
    AuxiliarySmartSheetTarget resolvedTarget = target.orElseThrow(
        () -> new IllegalStateException("辅助表尚未配置"));
    Map<String, Object> mappedFields = mappingResolver == null
        ? payload.fields()
        : mappingResolver.toSourceFields(resolvedTarget.role() + ":" + resolvedTarget.sheetId(), payload.fields());
    String sourceTable = resolvedTarget.role() + ":" + resolvedTarget.sheetId();
    String identityField = mappingResolver == null
        ? resolvedTarget.uniqueFieldTitle() : mappingResolver.sourceFieldFor(sourceTable, "phone");
    auxiliaryWriter.upsert(resolvedTarget, mappedFields, identityField, timeout);
  }

  private PendingWritePayload resolveExistingRow(Long customerId, String phone, PendingWritePayload payload) {
    if (!blank(payload.sourceTable()) && !blank(payload.sourceRowId())) {
      return payload;
    }
    Customer customer = customerId != null && customerId > 0
        ? customerQueryService.getById(customerId)
        : customerQueryService.getByPhone(phone);
    if (customer == null || blank(customer.getSourceTable()) || blank(customer.getSourceRowId())) {
      throw new IllegalStateException("customer source table or row id is still missing");
    }
    return new PendingWritePayload(customer.getSourceTable(), customer.getSourceRowId(), payload.fields(),
        payload.targetDocumentId(), payload.targetSheetId(), payload.targetViewId());
  }

  private MapPayload remotePayload(PendingWritePayload payload, TagExchangeResult exchange) {
    if (mappingResolver == null) {
      return new MapPayload(payload.sourceTable(), payload.sourceRowId(), exchange.acceptedFields());
    }
    return new MapPayload(
        payload.sourceTable(),
        payload.sourceRowId(),
        mappingResolver.toSourceFields(payload.sourceTable(), exchange.acceptedFields()));
  }

  private record MapPayload(String sourceTable, String sourceRowId, java.util.Map<String, Object> fields) {
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
