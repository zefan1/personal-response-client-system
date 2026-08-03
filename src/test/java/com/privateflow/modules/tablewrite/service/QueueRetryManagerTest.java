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
  void retrySkipsInvalidSelectValueButWritesRemainingDisplayFields() throws Exception {
    PendingTableWriteRepository repository = mock(PendingTableWriteRepository.class);
    WecomTableClient client = mock(WecomTableClient.class);
    TableConfigProvider config = mock(TableConfigProvider.class);
    TableFieldMappingResolver mapping = mock(TableFieldMappingResolver.class);
    TagExchangeService exchange = mock(TagExchangeService.class);
    ProfileProjectionFieldFilter projectionFilter = mock(ProfileProjectionFieldFilter.class);
    NewCustomerRowCreator creator = mock(NewCustomerRowCreator.class);
    PendingTableWrite item = new PendingTableWrite();
    item.setId(9L);
    item.setPhone("13434567622");
    item.setActionType(TableWriteActionType.INSERT);
    item.setRetryCount(0);
    item.setPayload(new ObjectMapper().writeValueAsString(new PendingWritePayload(
        "table_a", null, Map.of("customerStage", "意向初筛", "bodyConcerns", "腰痛"))));
    item.setNextRetryAt(LocalDateTime.now());
    when(repository.due(100)).thenReturn(List.of(item));
    when(repository.countStaleFailed(1)).thenReturn(0);
    when(config.get()).thenReturn(new TableConfig("", "", 5000, 3, 30, 1, "ADMIN", 50, 500));
    TagExchangeResult accepted = new TagExchangeResult(
        Map.of("customerStage", "意向初筛", "bodyConcerns", "腰痛"), List.of(), List.of());
    when(exchange.prepareOutbound(
        eq(TagExchangeSourceType.TABLE_DISPLAY_PROJECTION), eq("9"), any(Map.class))).thenReturn(accepted);
    when(projectionFilter.filter("table_a", accepted.acceptedFields(), Duration.ofMillis(5000)))
        .thenReturn(new ProfileProjectionFieldFilter.Result(
            Map.of("客户关注点", "腰痛"), List.of("客户阶段")));
    when(client.createRow("table_a", Map.of("客户关注点", "腰痛"), Duration.ofMillis(5000)))
        .thenReturn("row-9");

    QueueRetryManager manager = new QueueRetryManager(
        repository,
        client,
        config,
        new ObjectMapper(),
        mock(CustomerQueryService.class),
        creator,
        mapping,
        exchange,
        projectionFilter);

    manager.retryDueWrites();

    verify(client).createRow("table_a", Map.of("客户关注点", "腰痛"), Duration.ofMillis(5000));
    verify(repository).markResolved(9L);
  }

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
        Map.of("bodyConcerns", "我一直觉得腰痛、肚子也很大"), List.of(), List.of());
    when(exchange.prepareOutbound(
        eq(TagExchangeSourceType.TABLE_DISPLAY_PROJECTION), eq("7"), any(Map.class))).thenReturn(accepted);
    when(mapping.toSourceFields("table_a", accepted.acceptedFields()))
        .thenReturn(Map.of("客户关注点", "我一直觉得腰痛、肚子也很大"));
    when(client.createRow(eq("table_a"), eq(Map.of("客户关注点", "我一直觉得腰痛、肚子也很大")), any(Duration.class)))
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

    verify(exchange).prepareOutbound(
        TagExchangeSourceType.TABLE_DISPLAY_PROJECTION, "7", Map.of("bodyConcerns", "OLD_CODE"));
    verify(client).createRow(
        "table_a", Map.of("客户关注点", "我一直觉得腰痛、肚子也很大"), Duration.ofMillis(5000));
    verify(repository).markResolved(7L);
  }

  @Test
  void retryLinksCreatedRowToPhoneLessRecognitionCustomerByCustomerId() throws Exception {
    PendingTableWriteRepository repository = mock(PendingTableWriteRepository.class);
    WecomTableClient client = mock(WecomTableClient.class);
    TableConfigProvider config = mock(TableConfigProvider.class);
    TableFieldMappingResolver mapping = mock(TableFieldMappingResolver.class);
    TagExchangeService exchange = mock(TagExchangeService.class);
    NewCustomerRowCreator creator = mock(NewCustomerRowCreator.class);
    PendingTableWrite item = new PendingTableWrite();
    item.setId(8L);
    item.setCustomerId(42L);
    item.setActionType(TableWriteActionType.INSERT);
    item.setRetryCount(0);
    item.setPayload(new ObjectMapper().writeValueAsString(new PendingWritePayload(
        "table_a", null, Map.of("nickname", "匿名客户"))));
    item.setNextRetryAt(LocalDateTime.now());
    when(repository.due(100)).thenReturn(List.of(item));
    when(repository.countStaleFailed(1)).thenReturn(0);
    when(config.get()).thenReturn(new TableConfig("", "", 5000, 3, 30, 1, "ADMIN", 50, 500));
    TagExchangeResult accepted = new TagExchangeResult(Map.of("nickname", "匿名客户"), List.of(), List.of());
    when(exchange.prepareOutbound(eq(TagExchangeSourceType.TABLE_DISPLAY_PROJECTION), eq("8"), any(Map.class)))
        .thenReturn(accepted);
    when(mapping.toSourceFields("table_a", accepted.acceptedFields())).thenReturn(accepted.acceptedFields());
    when(client.createRow(eq("table_a"), eq(accepted.acceptedFields()), any(Duration.class))).thenReturn("row-42");

    QueueRetryManager manager = new QueueRetryManager(
        repository, client, config, new ObjectMapper(), mock(CustomerQueryService.class), creator, mapping, exchange);

    manager.retryDueWrites();

    verify(creator).insertCustomerAfterQueuedCreate(42L, null, "table_a", "row-42", accepted.acceptedFields());
    verify(repository).markResolved(8L);
  }
}
