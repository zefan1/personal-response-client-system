package com.privateflow.modules.tablewrite.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.tablewrite.PendingWritePayload;
import com.privateflow.modules.tablewrite.TableWriteActionType;
import com.privateflow.modules.tablewrite.client.AuxiliarySmartSheetWriter;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuxiliarySmartSheetProjectionServiceTest {

  @Test
  void projectsArrivalFieldsOnlyAndNeverWritesAssignmentIntakeBack() {
    AuxiliarySmartSheetWriter writer = mock(AuxiliarySmartSheetWriter.class);
    WriteQueueManager queueManager = mock(WriteQueueManager.class);
    AuxiliarySmartSheetTarget assignment = target("ASSIGNMENT", "doc-a", "sheet-a", "view-a");
    AuxiliarySmartSheetTarget arrival = target("ARRIVAL", "doc-r", "sheet-r", "view-r");
    AuxiliarySmartSheetProjectionService service = new AuxiliarySmartSheetProjectionService(
        writer, queueManager, Optional.of(assignment), Optional.of(arrival), java.time.Duration.ofSeconds(5));
    Customer customer = customer();

    service.project(customer);

    verify(writer).upsert(eq(arrival), eq(Map.of(
        "phone", "13800000000",
        "nickname", "妈妈A",
        "appointmentDate", "2026-08-20",
        "appointmentStore", "E2店",
        "appointmentItem", "产后修复",
        "arrived", "否",
        "assignedKeeper", "洁仪")), eq("手机号码"), any());
    verify(writer, org.mockito.Mockito.never()).upsert(eq(assignment), anyMap(), eq("联系方式"), any());
    verifyNoInteractions(queueManager);
  }

  @Test
  void skipsTargetsWithoutRelevantCustomerData() {
    AuxiliarySmartSheetWriter writer = mock(AuxiliarySmartSheetWriter.class);
    WriteQueueManager queueManager = mock(WriteQueueManager.class);
    AuxiliarySmartSheetProjectionService service = new AuxiliarySmartSheetProjectionService(
        writer, queueManager,
        Optional.of(target("ASSIGNMENT", "doc-a", "sheet-a", "view-a")),
        Optional.of(target("ARRIVAL", "doc-r", "sheet-r", "view-r")),
        java.time.Duration.ofSeconds(5));
    Customer customer = new Customer();
    customer.setPhone("13800000000");

    service.project(customer);

    verifyNoInteractions(writer, queueManager);
  }

  @Test
  void queuesFailedProjectionWithoutBlockingTheCustomerSync() {
    AuxiliarySmartSheetWriter writer = mock(AuxiliarySmartSheetWriter.class);
    WriteQueueManager queueManager = mock(WriteQueueManager.class);
    AuxiliarySmartSheetTarget arrival = target("ARRIVAL", "doc-r", "sheet-r", "view-r");
    AuxiliarySmartSheetProjectionService service = new AuxiliarySmartSheetProjectionService(
        writer, queueManager, Optional.empty(), Optional.of(arrival), java.time.Duration.ofSeconds(5));
    Customer customer = customer();
    customer.setId(7L);
    doThrow(new IllegalStateException("wecom unavailable"))
        .when(writer).upsert(eq(arrival), anyMap(), eq("手机号码"), any());

    service.project(customer);

    verify(queueManager).enqueue(
        eq(7L), eq("13800000000"), eq(TableWriteActionType.UPDATE),
        any(PendingWritePayload.class), eq("wecom unavailable"));
  }

  private static AuxiliarySmartSheetTarget target(String role, String doc, String sheet, String view) {
    return new AuxiliarySmartSheetTarget(role, doc, sheet, view, "https://example.test/" + role);
  }

  private static Customer customer() {
    Customer customer = new Customer();
    customer.setPhone("13800000000");
    customer.setNickname("妈妈A");
    customer.setLeadType("推广");
    customer.setAssignedKeeper("洁仪");
    customer.setIntendedStore("E2店");
    customer.setAppointmentDate(LocalDate.of(2026, 8, 20));
    customer.setAppointmentStore("E2店");
    customer.setAppointmentItem("产后修复");
    customer.setArrived("否");
    return customer;
  }
}
