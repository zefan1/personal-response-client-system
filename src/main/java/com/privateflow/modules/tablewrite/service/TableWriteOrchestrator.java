package com.privateflow.modules.tablewrite.service;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.common.events.CustomerFollowupAnalysisCompletedEvent;
import com.privateflow.common.events.ManualProfileUpdatedEvent;
import com.privateflow.common.events.RecognizedProfileFactsUpdatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.tablewrite.PendingWritePayload;
import com.privateflow.modules.tablewrite.TableWriteActionType;
import com.privateflow.modules.tablewrite.TableWriteException;
import com.privateflow.modules.tablewrite.TableWriteErrorCodes;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class TableWriteOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(TableWriteOrchestrator.class);
  private final CustomerQueryService customerQueryService;
  private final NewCustomerRowCreator newCustomerRowCreator;
  private final ExistingCustomerUpdater existingCustomerUpdater;
  private final WriteQueueManager queueManager;
  private final CustomerMasterProjectionService customerMasterProjectionService;
  private final AuxiliarySmartSheetProjectionService auxiliaryProjectionService;
  private final AuditLogger auditLogger;
  private final ObjectMapper objectMapper;

  @org.springframework.beans.factory.annotation.Autowired
  public TableWriteOrchestrator(
      CustomerQueryService customerQueryService,
      NewCustomerRowCreator newCustomerRowCreator,
      ExistingCustomerUpdater existingCustomerUpdater,
      WriteQueueManager queueManager,
      CustomerMasterProjectionService customerMasterProjectionService,
      AuxiliarySmartSheetProjectionService auxiliaryProjectionService,
      AuditLogger auditLogger,
      ObjectMapper objectMapper) {
    this.customerQueryService = customerQueryService;
    this.newCustomerRowCreator = newCustomerRowCreator;
    this.existingCustomerUpdater = existingCustomerUpdater;
    this.queueManager = queueManager;
    this.customerMasterProjectionService = customerMasterProjectionService;
    this.auxiliaryProjectionService = auxiliaryProjectionService;
    this.auditLogger = auditLogger;
    this.objectMapper = objectMapper;
  }

  public TableWriteOrchestrator(
      CustomerQueryService customerQueryService,
      NewCustomerRowCreator newCustomerRowCreator,
      ExistingCustomerUpdater existingCustomerUpdater,
      WriteQueueManager queueManager) {
    this(customerQueryService, newCustomerRowCreator, existingCustomerUpdater, queueManager,
        null, null, null, new ObjectMapper());
  }

  @Async("tableWriteExecutor")
  @EventListener
  public void onCustomerMessageSent(CustomerMessageSentEvent event) {
    Customer customer = event.customerId() != null && event.customerId() > 0
        ? customerQueryService.getById(event.customerId())
        : customerQueryService.getByPhone(event.phone());
    if (customer == null) {
      log.warn("skip table write event for missing customer id={}", event.customerId());
      return;
    }
    boolean shouldCreate = customer.getSourceRowId() == null || customer.getSourceRowId().isBlank();
    try {
      if (shouldCreate) {
        withOneImmediateRetry(() -> newCustomerRowCreator.create(event));
      } else {
        withOneImmediateRetry(() -> existingCustomerUpdater.update(customer, event));
      }
    } catch (RuntimeException ex) {
      if (ex instanceof TableWriteException tableWriteException
          && TableWriteErrorCodes.TABLE_WRITE_BLOCKED.equals(tableWriteException.getErrorCode())) {
        log.warn("skip smart table write because required unique value is unavailable, customerId={}", event.customerId());
        return;
      }
      enqueueFallback(event, customer, shouldCreate, ex);
    }
  }

  @Async("tableWriteExecutor")
  @EventListener
  public void onFollowupAnalysisCompleted(CustomerFollowupAnalysisCompletedEvent event) {
    if (event == null || event.phone() == null || event.phone().isBlank()) {
      return;
    }
    Customer customer = customerQueryService.getByPhone(event.phone());
    if (customer == null) {
      return;
    }
    Map<String, Object> fields = event.fields() == null ? Map.of() : event.fields();
    try {
      withOneImmediateRetry(() -> existingCustomerUpdater.updateFields(customer, fields));
    } catch (RuntimeException ex) {
      queueManager.enqueue(
          event.phone(),
          TableWriteActionType.UPDATE,
          new PendingWritePayload(retrySourceTable(customer), retrySourceRowId(customer), fields),
          ex.getMessage());
    }
  }

  /**
   * Recognition facts are safe to project only when the customer already has a known source row.
   * This deliberately does not create or match a Smart Sheet row from a nickname alone.
   */
  @Async("tableWriteExecutor")
  @EventListener
  public void onRecognizedProfileFactsUpdated(RecognizedProfileFactsUpdatedEvent event) {
    if (event == null || event.customerId() == null || event.customerId() <= 0
        || event.fields() == null || event.fields().isEmpty()) {
      return;
    }
    Customer customer = customerQueryService.getById(event.customerId());
    if (customer == null || blank(customer.getSourceTable()) || blank(customer.getSourceRowId())) {
      return;
    }
    if (customer.getSourceTable().startsWith("ASSIGNMENT:") && blank(customer.getPhone())) {
      log.info("skip recognized fact projection without a unique phone, customerId={}", event.customerId());
      return;
    }
    Map<String, Object> fields = new java.util.LinkedHashMap<>(event.fields());
    fields.keySet().removeIf(field -> field == null || field.isBlank());
    fields.values().removeIf(java.util.Objects::isNull);
    if (fields.isEmpty()) {
      return;
    }
    try {
      existingCustomerUpdater.updateFields(customer, fields);
    } catch (TableWriteException ex) {
      if (TableWriteErrorCodes.TABLE_WRITE_BLOCKED.equals(ex.getErrorCode())) {
        log.info("skip recognized fact projection blocked by identity, customerId={}", event.customerId());
        return;
      }
      queueManager.enqueue(
          customer.getId(),
          customer.getPhone(),
          TableWriteActionType.UPDATE,
          new PendingWritePayload(retrySourceTable(customer), retrySourceRowId(customer), fields),
          ex.getMessage());
    } catch (RuntimeException ex) {
      queueManager.enqueue(
          customer.getId(),
          customer.getPhone(),
          TableWriteActionType.UPDATE,
          new PendingWritePayload(retrySourceTable(customer), retrySourceRowId(customer), fields),
          ex.getMessage());
    }
  }

  /** Projects a committed manual profile edit without ever writing the assignment intake table. */
  @Async("tableWriteExecutor")
  @EventListener
  public void onManualProfileUpdated(ManualProfileUpdatedEvent event) {
    if (event == null || blank(event.phone()) || event.fields() == null || event.fields().isEmpty()) {
      return;
    }
    customerQueryService.refreshCache(event.phone());
    Customer customer = customerQueryService.getByPhone(event.phone());
    if (customer == null) {
      audit(event, "SKIPPED", "客户不存在", null);
      return;
    }
    Map<String, Object> fields = new java.util.LinkedHashMap<>(event.fields());
    fields.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null);
    if (fields.isEmpty()) {
      audit(event, "SKIPPED", "没有可写回字段", customer);
      return;
    }
    boolean assignmentSource = customer.getSourceTable() != null
        && customer.getSourceTable().startsWith("ASSIGNMENT:");
    boolean arrivalSource = customer.getSourceTable() != null
        && customer.getSourceTable().startsWith("ARRIVAL:");

    if (!assignmentSource) {
      writeSourceRow(event, customer, fields);
    }
    if ((assignmentSource || arrivalSource) && customerMasterProjectionService != null) {
      try {
        customerMasterProjectionService.projectFields(customer, fields);
        audit(event, "MASTER_SUCCESS", null, customer);
      } catch (RuntimeException ex) {
        queueManager.enqueue(customer.getId(), customer.getPhone(), TableWriteActionType.UPDATE,
            new PendingWritePayload("PRIMARY", null, fields), message(ex));
        audit(event, "MASTER_QUEUED", message(ex), customer);
      }
    }
    if (auxiliaryProjectionService != null) {
      auxiliaryProjectionService.project(customer);
    }
  }

  private void writeSourceRow(ManualProfileUpdatedEvent event, Customer customer, Map<String, Object> fields) {
    if (blank(customer.getSourceTable()) || blank(customer.getSourceRowId())) {
      audit(event, "SOURCE_SKIPPED", "客户没有可写回的来源行", customer);
      return;
    }
    try {
      existingCustomerUpdater.updateFields(customer, fields);
      audit(event, "SOURCE_SUCCESS", null, customer);
    } catch (RuntimeException ex) {
      queueManager.enqueue(customer.getId(), customer.getPhone(), TableWriteActionType.UPDATE,
          new PendingWritePayload(retrySourceTable(customer), retrySourceRowId(customer), fields), message(ex));
      audit(event, "SOURCE_QUEUED", message(ex), customer);
    }
  }

  private void audit(ManualProfileUpdatedEvent event, String status, String error, Customer customer) {
    if (auditLogger == null) {
      return;
    }
    Map<String, Object> detail = new java.util.LinkedHashMap<>();
    detail.put("fields", event.fields().keySet());
    detail.put("status", status);
    detail.put("sourceTable", customer == null ? null : customer.getSourceTable());
    detail.put("sourceRowId", customer == null ? null : customer.getSourceRowId());
    if (error != null) {
      detail.put("error", error);
    }
    try {
      auditLogger.log("SAVE_TO_TABLE", event.operator(), "customer", event.phone(),
          objectMapper.writeValueAsString(detail));
    } catch (JsonProcessingException ex) {
      auditLogger.log("SAVE_TO_TABLE", event.operator(), "customer", event.phone(),
          "automatic projection status=" + status);
    }
  }

  private void enqueueFallback(CustomerMessageSentEvent event, Customer customer, boolean create, RuntimeException ex) {
    TableWriteActionType actionType = create ? TableWriteActionType.INSERT : TableWriteActionType.UPDATE;
    PendingWritePayload payload;
    if (create) {
      payload = new PendingWritePayload(newCustomerRowCreator.resolveSourceTable(event.sourceTable()), null, newCustomerRowCreator.newCustomerFields(event));
    } else {
      String sourceTable = customer == null ? event.sourceTable() : customer.getSourceTable();
      String sourceRowId = customer == null ? null : customer.getSourceRowId();
      Map<String, Object> fields = existingCustomerUpdater.followupFields(event);
      payload = new PendingWritePayload(sourceTable, sourceRowId, fields);
    }
    queueManager.enqueue(event.customerId(), event.phone(), actionType, payload, ex.getMessage());
  }

  private void withOneImmediateRetry(Runnable action) {
    try {
      action.run();
    } catch (RuntimeException first) {
      if (first instanceof TableWriteException tableWriteException
          && TableWriteErrorCodes.TABLE_WRITE_BLOCKED.equals(tableWriteException.getErrorCode())) {
        throw tableWriteException;
      }
      sleepOneSecond();
      action.run();
    }
  }

  private void sleepOneSecond() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("table write retry interrupted", ex);
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String message(RuntimeException ex) {
    return blank(ex.getMessage()) ? ex.getClass().getSimpleName() : ex.getMessage();
  }

  private String retrySourceTable(Customer customer) {
    return customer != null && customer.getSourceTable() != null
        && customer.getSourceTable().startsWith("ASSIGNMENT:")
        ? "PRIMARY" : customer.getSourceTable();
  }

  private String retrySourceRowId(Customer customer) {
    return customer != null && customer.getSourceTable() != null
        && customer.getSourceTable().startsWith("ASSIGNMENT:")
        ? null : customer.getSourceRowId();
  }
}
