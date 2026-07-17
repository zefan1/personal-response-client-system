package com.privateflow.modules.customer.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.admin.DatasourceAdminRepository;
import com.privateflow.modules.customer.config.CustomerCacheProperties;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerCacheManager;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.service.CustomerMergeEngine;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeSourceType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CustomerSyncSchedulerTest {

  private DatasourceAdminRepository datasourceRepository;
  private SheetClient sheetClient;
  private CustomerSyncScheduler scheduler;

  @BeforeEach
  void setUp() {
    datasourceRepository = mock(DatasourceAdminRepository.class);
    sheetClient = mock(SheetClient.class);
    CustomerCacheProperties properties = new CustomerCacheProperties();
    properties.setMaxSyncRowsPerRound(100);
    scheduler = new CustomerSyncScheduler(
        datasourceRepository,
        sheetClient,
        mock(FieldMappingResolver.class),
        mock(CustomerRepository.class),
        mock(CustomerCacheManager.class),
        mock(CustomerMergeEngine.class),
        mock(SyncFailureRepository.class),
        properties,
        mock(ApplicationEventPublisher.class));
  }

  @Test
  void runOnceUsesEnabledDatasourcesFromDatabase() {
    SheetSource first = new SheetSource(1L, "sheet-a", "table_a");
    SheetSource second = new SheetSource(2L, "sheet-b", "table_b");
    when(datasourceRepository.enabledSources()).thenReturn(List.of(first, second));
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), eq(100))).thenReturn(List.of());

    scheduler.runOnce();

    verify(sheetClient).fetchIncrementalRows(eq(first), any(LocalDateTime.class), eq(100));
    verify(sheetClient).fetchIncrementalRows(eq(second), any(LocalDateTime.class), eq(100));
  }

  @Test
  void runOneSyncsOnlyRequestedDatasource() {
    SheetSource source = new SheetSource(7L, "sheet-only", "table_only");
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), eq(100)))
        .thenReturn(List.of(new SheetRow("row-without-phone", Map.of("name", "Alice"))));

    scheduler.runOne(source);

    verify(datasourceRepository, never()).enabledSources();
    verify(sheetClient).fetchIncrementalRows(eq(source), any(LocalDateTime.class), eq(100));
  }

  @Test
  void unmatchedTagResultStillUpsertsCustomerAndUpdatesCache() {
    SheetSource source = new SheetSource(7L, "sheet-only", "table_only");
    Customer incoming = new Customer();
    incoming.setPhone("13800000000");
    TagExchangeResult exchange = new TagExchangeResult(Map.of(), List.of(), List.of());
    FieldMappingResult mapping = new FieldMappingResult(incoming, exchange);
    FieldMappingResolver resolver = mock(FieldMappingResolver.class);
    CustomerRepository repository = mock(CustomerRepository.class);
    CustomerCacheManager cache = mock(CustomerCacheManager.class);
    CustomerMergeEngine merge = mock(CustomerMergeEngine.class);
    scheduler = scheduler(resolver, repository, cache, merge);
    when(resolver.mapRowResult(eq("table_only"), any(SheetRow.class))).thenReturn(mapping);
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), eq(100)))
        .thenReturn(List.of(new SheetRow("row-1", Map.of("phone", "13800000000"))));
    when(repository.findByPhone("13800000000"))
        .thenReturn(java.util.Optional.empty(), java.util.Optional.of(incoming));
    when(merge.merge(incoming, null)).thenReturn(incoming);

    scheduler.runOne(source);

    verify(repository).upsert(incoming, exchange, TagExchangeSourceType.EXTERNAL_SYNC, "row-1");
    verify(cache).write(incoming);
  }

  @Test
  void databaseFailureRecordsSyncFailureAndDoesNotWriteCache() {
    SheetSource source = new SheetSource(7L, "sheet-only", "table_only");
    Customer incoming = new Customer();
    incoming.setPhone("13800000000");
    TagExchangeResult exchange = new TagExchangeResult(Map.of(), List.of(), List.of());
    FieldMappingResolver resolver = mock(FieldMappingResolver.class);
    CustomerRepository repository = mock(CustomerRepository.class);
    CustomerCacheManager cache = mock(CustomerCacheManager.class);
    CustomerMergeEngine merge = mock(CustomerMergeEngine.class);
    SyncFailureRepository failures = mock(SyncFailureRepository.class);
    scheduler = scheduler(resolver, repository, cache, merge, failures);
    when(resolver.mapRowResult(eq("table_only"), any(SheetRow.class)))
        .thenReturn(new FieldMappingResult(incoming, exchange));
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), eq(100)))
        .thenReturn(List.of(new SheetRow("row-1", Map.of("phone", "13800000000"))));
    when(repository.findByPhone("13800000000")).thenReturn(java.util.Optional.empty());
    when(merge.merge(incoming, null)).thenReturn(incoming);
    doThrow(new IllegalStateException("database down"))
        .when(repository).upsert(incoming, exchange, TagExchangeSourceType.EXTERNAL_SYNC, "row-1");

    scheduler.runOne(source);

    verify(failures).record(eq("table_only"), eq("row-1"), eq("13800000000"), eq("database down"), any());
    verify(cache, never()).write(any());
  }

  private CustomerSyncScheduler scheduler(
      FieldMappingResolver resolver,
      CustomerRepository repository,
      CustomerCacheManager cache,
      CustomerMergeEngine merge) {
    return scheduler(resolver, repository, cache, merge, mock(SyncFailureRepository.class));
  }

  private CustomerSyncScheduler scheduler(
      FieldMappingResolver resolver,
      CustomerRepository repository,
      CustomerCacheManager cache,
      CustomerMergeEngine merge,
      SyncFailureRepository failures) {
    CustomerCacheProperties properties = new CustomerCacheProperties();
    properties.setMaxSyncRowsPerRound(100);
    return new CustomerSyncScheduler(
        datasourceRepository,
        sheetClient,
        resolver,
        repository,
        cache,
        merge,
        failures,
        properties,
        mock(ApplicationEventPublisher.class));
  }
}
