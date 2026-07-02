package com.privateflow.modules.tablewrite.service;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.tablewrite.PendingWritePayload;
import com.privateflow.modules.tablewrite.TableWriteActionType;
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
    if (event.phone() == null || event.phone().isBlank()) {
      log.warn("skip table write event without phone");
      return;
    }
    Customer customer = customerQueryService.getByPhone(event.phone());
    boolean shouldCreate = customer == null || event.isNewCustomer();
    try {
      if (shouldCreate) {
        withOneImmediateRetry(() -> newCustomerRowCreator.create(event));
      } else {
        withOneImmediateRetry(() -> existingCustomerUpdater.update(customer, event));
      }
    } catch (RuntimeException ex) {
      enqueueFallback(event, customer, shouldCreate, ex);
    }
  }

  private void enqueueFallback(CustomerMessageSentEvent event, Customer customer, boolean create, RuntimeException ex) {
    TableWriteActionType actionType = create ? TableWriteActionType.INSERT : TableWriteActionType.UPDATE;
    PendingWritePayload payload;
    if (create) {
      payload = new PendingWritePayload(event.sourceTable(), null, newCustomerRowCreator.newCustomerFields(event));
    } else {
      String sourceTable = customer == null ? event.sourceTable() : customer.getSourceTable();
      String sourceRowId = customer == null ? null : customer.getSourceRowId();
      Map<String, Object> fields = existingCustomerUpdater.followupFields(event);
      payload = new PendingWritePayload(sourceTable, sourceRowId, fields);
    }
    queueManager.enqueue(event.phone(), actionType, payload, ex.getMessage());
  }

  private void withOneImmediateRetry(Runnable action) {
    try {
      action.run();
    } catch (RuntimeException first) {
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
