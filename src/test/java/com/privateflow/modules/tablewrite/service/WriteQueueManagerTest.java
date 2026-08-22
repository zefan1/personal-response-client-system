package com.privateflow.modules.tablewrite.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.tablewrite.PendingWritePayload;
import com.privateflow.modules.tablewrite.TableWriteActionType;
import com.privateflow.modules.tablewrite.config.TableConfig;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.PendingTableWriteRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WriteQueueManagerTest {

  private final PendingTableWriteRepository repository = mock(PendingTableWriteRepository.class);
  private final TableConfigProvider config = mock(TableConfigProvider.class);
  private final WriteQueueManager manager = new WriteQueueManager(repository, config, new ObjectMapper());

  @Test
  void doesNotQueueInsertWithoutUniquePhone() {
    manager.enqueue(44L, null, TableWriteActionType.INSERT,
        new PendingWritePayload("th1zyU", null, Map.of("nickname", "仅昵称")), "missing phone");

    verify(repository, never()).enqueue(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void doesNotQueueUpdateWithoutRowOrCustomerIdentity() {
    manager.enqueue(null, null, TableWriteActionType.UPDATE,
        new PendingWritePayload("private_customers", null, Map.of("followupNotes", "x")), "missing row");

    verify(repository, never()).enqueue(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void allowsAuxiliaryUpsertWithoutSourceRowWhenPhoneExists() {
    when(config.get()).thenReturn(new TableConfig("", "", 10000, 5, 60, 1, "ADMIN", 100, 1000));
    manager.enqueue(44L, "13800001111", TableWriteActionType.UPDATE,
        new PendingWritePayload("ASSIGNMENT", null, Map.of("assignedKeeper", "keeper")), "relay timeout");

    verify(repository).enqueue(
        org.mockito.ArgumentMatchers.eq(44L), org.mockito.ArgumentMatchers.eq("13800001111"),
        org.mockito.ArgumentMatchers.eq(TableWriteActionType.UPDATE), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("relay timeout"));
  }
}
