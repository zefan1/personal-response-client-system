package com.privateflow.modules.tablewrite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.tablewrite.PendingWritePayload;
import com.privateflow.modules.tablewrite.TableWriteActionType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TableWriteOrchestratorTest {

  private final CustomerQueryService customerQueryService = mock(CustomerQueryService.class);
  private final NewCustomerRowCreator newCustomerRowCreator = mock(NewCustomerRowCreator.class);
  private final ExistingCustomerUpdater existingCustomerUpdater = mock(ExistingCustomerUpdater.class);
  private final WriteQueueManager queueManager = mock(WriteQueueManager.class);
  private final TableWriteOrchestrator orchestrator = new TableWriteOrchestrator(
      customerQueryService,
      newCustomerRowCreator,
      existingCustomerUpdater,
      queueManager);

  @Test
  void updatesExistingCustomerWithFullPhoneAndFallsBackToPendingQueueOnFailure() {
    Customer customer = new Customer();
    customer.setPhone("18800001111");
    customer.setSourceTable("私域客资管理表");
    customer.setSourceRowId("row-1111");
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    CustomerMessageSentEvent event = sentEvent();
    doThrow(new IllegalStateException("table down")).when(existingCustomerUpdater).update(customer, event);
    when(existingCustomerUpdater.followupFields(event)).thenReturn(java.util.Map.of(
        "followupNotes", "建议今天预约到店评估",
        "nextFollowupDir", "NEXT_STEP"));

    orchestrator.onCustomerMessageSent(event);

    verify(existingCustomerUpdater, times(2)).update(customer, event);
    ArgumentCaptor<PendingWritePayload> payloadCaptor = ArgumentCaptor.forClass(PendingWritePayload.class);
    verify(queueManager).enqueue(
        org.mockito.Mockito.eq("18800001111"),
        org.mockito.Mockito.eq(TableWriteActionType.UPDATE),
        payloadCaptor.capture(),
        org.mockito.Mockito.eq("table down"));
    PendingWritePayload payload = payloadCaptor.getValue();
    assertThat(payload.sourceTable()).isEqualTo("私域客资管理表");
    assertThat(payload.sourceRowId()).isEqualTo("row-1111");
    assertThat(payload.fields()).containsEntry("nextFollowupDir", "NEXT_STEP");
  }

  private CustomerMessageSentEvent sentEvent() {
    return new CustomerMessageSentEvent(
        "18800001111",
        "Alice",
        false,
        "私域客资管理表",
        "TUAN_GOU",
        "建议今天预约到店评估",
        java.util.List.of(),
        "建议今天预约到店评估",
        "NEXT_STEP",
        null,
        "keeper-1");
  }
}
