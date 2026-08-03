package com.privateflow.modules.tablewrite.service;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.common.events.CustomerFollowupAnalysisCompletedEvent;
import com.privateflow.common.events.CustomerTableSyncRequestedEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
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

  public TableWriteOrchestrator(
      CustomerQueryService customerQueryService,
      NewCustomerRowCreator newCustomerRowCreator,
      ExistingCustomerUpdater existingCustomerUpdater,
      WriteQueueManager queueManager) {
    this.customerQueryService = customerQueryService;
    this.newCustomerRowCreator = newCustomerRowCreator;
    this.existingCustomerUpdater = existingCustomerUpdater;
    this.queueManager = queueManager;
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
          new PendingWritePayload(customer.getSourceTable(), customer.getSourceRowId(), fields),
          ex.getMessage());
    }
  }

  @Async("tableWriteExecutor")
  @EventListener
  public void onCustomerTableSyncRequested(CustomerTableSyncRequestedEvent event) {
    if (event == null || event.customerId() <= 0) {
      return;
    }
    Customer customer = customerQueryService.getById(event.customerId());
    if (customer == null || customer.getPhone() == null || customer.getPhone().isBlank()) {
      log.warn("skip smart table sync request for missing customer or phone, customerId={}", event.customerId());
      return;
    }
    boolean shouldCreate = customer.getSourceRowId() == null || customer.getSourceRowId().isBlank();
    try {
      if (shouldCreate) {
        withOneImmediateRetry(() -> newCustomerRowCreator.create(customer));
      } else {
        withOneImmediateRetry(() -> existingCustomerUpdater.updateFields(customer, syncFields(customer)));
      }
    } catch (RuntimeException ex) {
      TableWriteActionType actionType = shouldCreate ? TableWriteActionType.INSERT : TableWriteActionType.UPDATE;
      Map<String, Object> fields = shouldCreate
          ? newCustomerRowCreator.newCustomerFields(customer)
          : syncFields(customer);
      String sourceTable = shouldCreate
          ? newCustomerRowCreator.resolveSourceTable(customer.getSourceTable())
          : customer.getSourceTable();
      queueManager.enqueue(
          customer.getId(),
          customer.getPhone(),
          actionType,
          new PendingWritePayload(sourceTable, shouldCreate ? null : customer.getSourceRowId(), fields),
          ex.getMessage());
    }
  }

  private Map<String, Object> syncFields(Customer customer) {
    return newCustomerRowCreator.newCustomerFields(customer);
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
}
