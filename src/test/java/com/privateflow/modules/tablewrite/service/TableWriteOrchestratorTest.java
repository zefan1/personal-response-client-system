package com.privateflow.modules.tablewrite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.common.events.CustomerFollowupAnalysisCompletedEvent;
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
        "nextFollowupAt", "",
        "nextFollowupDir", ""));

    orchestrator.onCustomerMessageSent(event);

    verify(existingCustomerUpdater, times(2)).update(customer, event);
    ArgumentCaptor<PendingWritePayload> payloadCaptor = ArgumentCaptor.forClass(PendingWritePayload.class);
    verify(queueManager).enqueue(
        org.mockito.Mockito.isNull(),
        org.mockito.Mockito.eq("18800001111"),
        org.mockito.Mockito.eq(TableWriteActionType.UPDATE),
        payloadCaptor.capture(),
        org.mockito.Mockito.eq("table down"));
    PendingWritePayload payload = payloadCaptor.getValue();
    assertThat(payload.sourceTable()).isEqualTo("私域客资管理表");
    assertThat(payload.sourceRowId()).isEqualTo("row-1111");
    assertThat(payload.fields()).containsEntry("followupNotes", "建议今天预约到店评估");
    assertThat(payload.fields()).containsEntry("nextFollowupAt", "");
    assertThat(payload.fields()).containsEntry("nextFollowupDir", "");
    assertThat(payload.fields()).doesNotContainKey("lastFollowupAt");
  }

  @Test
  void retriedAnalysisUsesTheExistingTableWriteRetryPath() {
    Customer customer = new Customer();
    customer.setPhone("18800001111");
    customer.setSourceTable("私域客资管理表");
    customer.setSourceRowId("row-1111");
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    java.util.Map<String, Object> fields = java.util.Map.of(
        "internalNote", "内部提醒",
        "followupNotes", "本次跟进记录");
    doThrow(new IllegalStateException("table down"))
        .when(existingCustomerUpdater).updateFields(customer, fields);

    orchestrator.onFollowupAnalysisCompleted(
        new CustomerFollowupAnalysisCompletedEvent("18800001111", fields));

    verify(existingCustomerUpdater, times(2)).updateFields(customer, fields);
    verify(queueManager).enqueue(
        "18800001111",
        TableWriteActionType.UPDATE,
        new PendingWritePayload("私域客资管理表", "row-1111", fields),
        "table down");
  }

  @Test
  void queuesPhoneLessRecognitionCustomerByCustomerIdWhenInitialTableCreateFails() {
    Customer customer = new Customer();
    customer.setId(42L);
    customer.setNickname("匿名客户");
    customer.setLeadType("XIAN_SUO");
    customer.setSourceTable("private_customers");
    when(customerQueryService.getById(42L)).thenReturn(customer);
    CustomerMessageSentEvent event = new CustomerMessageSentEvent(
        null, "匿名客户", true, "private_customers", "XIAN_SUO", "首次咨询", java.util.List.of(),
        "已发送回复", "NEXT", null, false, java.util.Map.of("followupNotes", "首次咨询"), "keeper", 42L);
    java.util.Map<String, Object> queuedFields = new java.util.LinkedHashMap<>();
    queuedFields.put("phone", null);
    queuedFields.put("nickname", "匿名客户");
    queuedFields.put("leadType", "XIAN_SUO");
    queuedFields.put("customerStage", "待联系");
    queuedFields.put("followupNotes", "首次咨询");
    when(newCustomerRowCreator.newCustomerFields(event)).thenReturn(queuedFields);
    when(newCustomerRowCreator.resolveSourceTable("private_customers")).thenReturn("private_customers");
    doThrow(new IllegalStateException("table down")).when(newCustomerRowCreator).create(event);

    orchestrator.onCustomerMessageSent(event);

    verify(newCustomerRowCreator, times(2)).create(event);
    verify(queueManager).enqueue(
        42L,
        null,
        TableWriteActionType.INSERT,
        new PendingWritePayload("private_customers", null, queuedFields),
        "table down");
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
        true,
        "keeper-1");
  }
}
