package com.privateflow.modules.tablewrite.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.tablewrite.TableWriteErrorCodes;
import com.privateflow.modules.tablewrite.TableWriteException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TableWritePhoneLessBlockTest {

  @Test
  void doesNotQueueAnUnsolvablePhoneLessInsert() {
    CustomerQueryService customers = mock(CustomerQueryService.class);
    NewCustomerRowCreator creator = mock(NewCustomerRowCreator.class);
    ExistingCustomerUpdater updater = mock(ExistingCustomerUpdater.class);
    WriteQueueManager queue = mock(WriteQueueManager.class);
    TableWriteOrchestrator orchestrator = new TableWriteOrchestrator(customers, creator, updater, queue);
    Customer customer = new Customer();
    customer.setId(44L);
    when(customers.getById(44L)).thenReturn(customer);
    CustomerMessageSentEvent event = new CustomerMessageSentEvent(
        null, "仅昵称", true, "th1zyU", "XIAN_SUO", "首次咨询", List.of(),
        "已发送回复", "NEXT", null, false, Map.of("followupNotes", "首次咨询"), "keeper", 44L);
    doThrow(new TableWriteException(
        TableWriteErrorCodes.TABLE_WRITE_BLOCKED,
        "smart table create is blocked because the configured unique field has no phone value"))
        .when(creator).create(event);

    orchestrator.onCustomerMessageSent(event);

    verify(creator).create(event);
    verify(queue, never()).enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }
}
