package com.privateflow.modules.tablewrite.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
import com.privateflow.modules.tablewrite.PendingTableWrite;
import com.privateflow.modules.tablewrite.PendingWritePayload;
import com.privateflow.modules.tablewrite.TableWriteActionType;
import com.privateflow.modules.tablewrite.client.WecomTableClient;
import com.privateflow.modules.tablewrite.config.TableConfig;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.PendingTableWriteRepository;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import com.privateflow.modules.customer.CustomerQueryService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QueueRetryManagerTest {

  @Test
  void retryRevalidatesPayloadAndWritesOnlyAcceptedFields() throws Exception {
    PendingTableWriteRepository repository = mock(PendingTableWriteRepository.class);
    WecomTableClient client = mock(WecomTableClient.class);
    TableConfigProvider config = mock(TableConfigProvider.class);
    TableFieldMappingResolver mapping = mock(TableFieldMappingResolver.class);
    TagExchangeService exchange = mock(TagExchangeService.class);
    NewCustomerRowCreator creator = mock(NewCustomerRowCreator.class);
    PendingTableWrite item = new PendingTableWrite();
    item.setId(7L);
    item.setPhone("13800000000");
    item.setActionType(TableWriteActionType.INSERT);
    item.setRetryCount(0);
    item.setPayload(new ObjectMapper().writeValueAsString(new PendingWritePayload(
        "table_a", null, Map.of("bodyConcerns", "OLD_CODE"))));
    item.setNextRetryAt(LocalDateTime.now());
    when(repository.due(100)).thenReturn(List.of(item));
    when(repository.countStaleFailed(1)).thenReturn(0);
    when(config.get()).thenReturn(new TableConfig("", "", 5000, 3, 30, 1, "ADMIN", 50, 500));
    TagExchangeResult accepted = new TagExchangeResult(
        Map.of("bodyConcerns", "URINE_LEAKAGE"), List.of(), List.of());
    when(exchange.prepareOutbound(
        eq(TagExchangeSourceType.TABLE_WRITE), eq("7"), any(Map.class))).thenReturn(accepted);
    when(mapping.toSourceFields("table_a", accepted.acceptedFields()))
        .thenReturn(Map.of("tag_column", "URINE_LEAKAGE"));
    when(client.createRow(eq("table_a"), eq(Map.of("tag_column", "URINE_LEAKAGE")), any(Duration.class)))
        .thenReturn("row-7");

    QueueRetryManager manager = new QueueRetryManager(
        repository,
        client,
        config,
        new ObjectMapper(),
        mock(CustomerQueryService.class),
        creator,
        mapping,
        exchange);

    manager.retryDueWrites();

    verify(exchange).prepareOutbound(TagExchangeSourceType.TABLE_WRITE, "7", Map.of("bodyConcerns", "OLD_CODE"));
    verify(client).createRow("table_a", Map.of("tag_column", "URINE_LEAKAGE"), Duration.ofMillis(5000));
    verify(repository).markResolved(7L);
  }
}
