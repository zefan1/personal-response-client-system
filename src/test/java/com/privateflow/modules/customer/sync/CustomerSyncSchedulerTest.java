package com.privateflow.modules.customer.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.admin.DatasourceAdminRepository;
import com.privateflow.modules.customer.config.CustomerCacheProperties;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.Account;
import com.privateflow.modules.api.auth.AccountRepository;
import com.privateflow.modules.customer.infra.CustomerCacheManager;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.service.CustomerMergeEngine;
import com.privateflow.common.events.NewLeadEvent;
import com.privateflow.modules.tablewrite.service.AuxiliarySmartSheetProjectionService;
import com.privateflow.modules.tablewrite.service.CustomerMasterProjectionService;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeSourceType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    verify(datasourceRepository).saveSuccessfulRemoteRead(eq("table_a"), any(LocalDateTime.class));
    verify(datasourceRepository).saveSuccessfulRemoteRead(eq("table_b"), any(LocalDateTime.class));
  }

  @Test
  void scheduledSyncRunsTheSameEnabledDatasourceLoop() {
    SheetSource source = new SheetSource(1L, "sheet-a", "table_a");
    when(datasourceRepository.enabledSources()).thenReturn(List.of(source));
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), eq(100))).thenReturn(List.of());

    scheduler.scheduledSync();

    verify(sheetClient).fetchIncrementalRows(eq(source), any(LocalDateTime.class), eq(100));
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
  void manualFirstSyncReadsTheWholeDatasourceAndPersistsItsWatermark() {
    SheetSource source = new SheetSource(7L, "sheet-only", "table_only");
    when(datasourceRepository.lastSuccessfulSync("table_only")).thenReturn(Optional.empty());
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), eq(100))).thenReturn(List.of());

    scheduler.runOne(source);

    org.mockito.ArgumentCaptor<LocalDateTime> modifiedAfter =
        org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
    verify(sheetClient).fetchIncrementalRows(eq(source), modifiedAfter.capture(), eq(100));
    assertThat(modifiedAfter.getValue()).isEqualTo(LocalDateTime.of(2000, 1, 1, 0, 0));
    verify(datasourceRepository).saveSuccessfulSync(eq("table_only"), any(LocalDateTime.class));
    verify(datasourceRepository).saveSuccessfulRemoteRead(eq("table_only"), any(LocalDateTime.class));
  }

  @Test
  void scheduledFirstSyncStaysIncrementalUntilAnOperatorStartsTheBackfill() {
    SheetSource source = new SheetSource(7L, "sheet-only", "table_only");
    when(datasourceRepository.enabledSources()).thenReturn(List.of(source));
    when(datasourceRepository.lastSuccessfulSync("table_only")).thenReturn(Optional.empty());
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), eq(100))).thenReturn(List.of());

    scheduler.runOnce();

    org.mockito.ArgumentCaptor<LocalDateTime> modifiedAfter =
        org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
    verify(sheetClient).fetchIncrementalRows(eq(source), modifiedAfter.capture(), eq(100));
    assertThat(modifiedAfter.getValue()).isAfter(LocalDateTime.now().minusDays(2));
    assertThat(modifiedAfter.getValue()).isBefore(LocalDateTime.now().minusHours(23));
  }

  @Test
  void forcedFullSyncIgnoresAnExistingWatermark() {
    SheetSource source = new SheetSource(7L, "sheet-only", "table_only");
    when(datasourceRepository.lastSuccessfulSync("table_only"))
        .thenReturn(Optional.of(LocalDateTime.now().minusMinutes(5)));
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), eq(100))).thenReturn(List.of());

    assertThat(scheduler.tryStartOneAsync(source, true)).isTrue();

    org.mockito.ArgumentCaptor<LocalDateTime> modifiedAfter =
        org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
    verify(sheetClient, org.mockito.Mockito.timeout(5000))
        .fetchIncrementalRows(eq(source), modifiedAfter.capture(), eq(100));
    assertThat(modifiedAfter.getValue()).isEqualTo(LocalDateTime.of(2000, 1, 1, 0, 0));
  }

  @Test
  void inboundIncrementalSyncDoesNotWriteRowsBackToWecom() {
    SheetSource source = new SheetSource(7L, "sheet-only", "ASSIGNMENT:sheet-only");
    Customer incoming = new Customer();
    incoming.setPhone("13800000000");
    TagExchangeResult exchange = new TagExchangeResult(Map.of(), List.of(), List.of());
    FieldMappingResolver resolver = mock(FieldMappingResolver.class);
    CustomerRepository repository = mock(CustomerRepository.class);
    CustomerMergeEngine merge = mock(CustomerMergeEngine.class);
    AuxiliarySmartSheetProjectionService projection = mock(AuxiliarySmartSheetProjectionService.class);
    CustomerMasterProjectionService masterProjection = mock(CustomerMasterProjectionService.class);
    scheduler = new CustomerSyncScheduler(
        datasourceRepository, sheetClient, resolver, repository, mock(CustomerCacheManager.class), merge,
        mock(SyncFailureRepository.class), new CustomerCacheProperties(), mock(ApplicationEventPublisher.class),
        projection, masterProjection, null);
    when(datasourceRepository.lastSuccessfulSync(source.sourceTable()))
        .thenReturn(Optional.of(LocalDateTime.now().minusMinutes(1)));
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), any(Integer.class)))
        .thenReturn(List.of(new SheetRow("row-1", Map.of("手机号码", "13800000000"))));
    when(resolver.mapRowResult(eq(source.sourceTable()), any(SheetRow.class)))
        .thenReturn(new FieldMappingResult(incoming, exchange));
    when(repository.findByPhone("13800000000"))
        .thenReturn(java.util.Optional.empty(), java.util.Optional.of(incoming));
    when(merge.merge(incoming, null)).thenReturn(incoming);

    scheduler.runOne(source);

    verifyNoInteractions(projection, masterProjection);
  }

  @Test
  void assignmentDisplayNameResolvesToAccountNameAndRaisesNewLead() {
    SheetSource source = new SheetSource(7L, "sheet-only", "ASSIGNMENT:sheet-only");
    Customer incoming = new Customer();
    incoming.setPhone("13800000000");
    incoming.setAssignedKeeper("System Admin");
    incoming.setAssignedAt(LocalDateTime.now());
    incoming.setLeadType("TUAN_GOU");
    FieldMappingResolver resolver = mock(FieldMappingResolver.class);
    CustomerRepository repository = mock(CustomerRepository.class);
    CustomerMergeEngine merge = mock(CustomerMergeEngine.class);
    AccountRepository accounts = mock(AccountRepository.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    scheduler = new CustomerSyncScheduler(
        datasourceRepository, sheetClient, resolver, repository, mock(CustomerCacheManager.class), merge,
        mock(SyncFailureRepository.class), new CustomerCacheProperties(), events,
        null, null, null, accounts);
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), any(Integer.class)))
        .thenReturn(List.of(new SheetRow("row-1", Map.of("手机号码", "13800000000"))));
    when(resolver.mapRowResult(eq(source.sourceTable()), any(SheetRow.class)))
        .thenReturn(new FieldMappingResult(incoming, new TagExchangeResult(Map.of(), List.of(), List.of())));
    when(accounts.resolveEnabledDisplayName("System Admin")).thenReturn(Optional.of("System Admin"));
    when(repository.findByPhone("13800000000"))
        .thenReturn(Optional.empty(), Optional.of(incoming));
    when(merge.merge(incoming, null)).thenReturn(incoming);

    scheduler.runOne(source);

    assertThat(incoming.getAssignedKeeper()).isEqualTo("System Admin");
    org.mockito.ArgumentCaptor<NewLeadEvent> event = org.mockito.ArgumentCaptor.forClass(NewLeadEvent.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue()).isEqualTo(new NewLeadEvent("13800000000", "TUAN_GOU", source.sourceTable()));
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
  void externalInvalidStageMarksLeadProcessedAndInvalid() {
    SheetSource source = new SheetSource(7L, "sheet-only", "table_only");
    Customer incoming = new Customer();
    incoming.setPhone("13800000000");
    incoming.setCustomerStage("无效线索");
    Customer existing = new Customer();
    existing.setPhone(incoming.getPhone());
    FieldMappingResolver resolver = mock(FieldMappingResolver.class);
    CustomerRepository repository = mock(CustomerRepository.class);
    CustomerMergeEngine merge = mock(CustomerMergeEngine.class);
    scheduler = scheduler(resolver, repository, mock(CustomerCacheManager.class), merge);
    when(resolver.mapRowResult(eq("table_only"), any(SheetRow.class)))
        .thenReturn(new FieldMappingResult(incoming,
            new TagExchangeResult(Map.of(), List.of(), List.of()),
            java.util.Set.of("customerStage")));
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), eq(100)))
        .thenReturn(List.of(new SheetRow("row-1", Map.of("phone", "13800000000"))));
    when(repository.findByPhone("13800000000"))
        .thenReturn(Optional.of(existing), Optional.of(incoming));
    when(merge.merge(incoming, existing)).thenReturn(incoming);

    scheduler.runOne(source);

    assertThat(incoming.isLeadInvalid()).isTrue();
    assertThat(incoming.getLeadInitialProcessedBy()).isEqualTo("企业微信同步");
    assertThat(incoming.getLeadInitialProcessedAt()).isNotNull();
    assertThat(incoming.getLeadRetainedUntil()).isAfter(incoming.getLeadInitialProcessedAt());
    verify(repository).upsert(incoming, new TagExchangeResult(Map.of(), List.of(), List.of()),
        TagExchangeSourceType.EXTERNAL_SYNC, "row-1");
    verify(repository).updateLeadProcessingState(incoming);
  }

  @Test
  void identicalWecomStageReadbackDoesNotIncrementCustomerVersionOrOverwriteOperator() {
    SheetSource source = new SheetSource(7L, "sheet-only", "table_only");
    LocalDateTime processedAt = LocalDateTime.of(2026, 8, 24, 9, 41, 37);
    Customer existing = new Customer();
    existing.setPhone("13800000000");
    existing.setCustomerStage("无效线索");
    existing.setLeadInvalid(true);
    existing.setLeadInitialProcessedAt(processedAt);
    existing.setLeadInitialProcessedBy("desktop");
    existing.setLeadRetainedUntil(processedAt.plusDays(1));
    Customer incoming = new Customer();
    incoming.setPhone(existing.getPhone());
    incoming.setCustomerStage(existing.getCustomerStage());
    Customer merged = new Customer();
    merged.setPhone(existing.getPhone());
    merged.setCustomerStage(existing.getCustomerStage());
    merged.setLeadInvalid(existing.isLeadInvalid());
    merged.setLeadInitialProcessedAt(existing.getLeadInitialProcessedAt());
    merged.setLeadInitialProcessedBy(existing.getLeadInitialProcessedBy());
    merged.setLeadRetainedUntil(existing.getLeadRetainedUntil());
    FieldMappingResolver resolver = mock(FieldMappingResolver.class);
    CustomerRepository repository = mock(CustomerRepository.class);
    CustomerCacheManager cache = mock(CustomerCacheManager.class);
    CustomerMergeEngine merge = mock(CustomerMergeEngine.class);
    scheduler = scheduler(resolver, repository, cache, merge);
    when(resolver.mapRowResult(eq("table_only"), any(SheetRow.class)))
        .thenReturn(new FieldMappingResult(incoming,
            new TagExchangeResult(Map.of(), List.of(), List.of()),
            java.util.Set.of("customerStage")));
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), eq(100)))
        .thenReturn(List.of(new SheetRow("row-1", Map.of("phone", "13800000000"))));
    when(repository.findByPhone(existing.getPhone())).thenReturn(Optional.of(existing));
    when(merge.merge(incoming, existing)).thenReturn(merged);

    scheduler.runOne(source);

    verify(repository, never()).upsert(any(), any(), any(), any());
    verify(repository, never()).updateLeadProcessingState(any());
    verifyNoInteractions(cache);
    assertThat(merged.getLeadInitialProcessedBy()).isEqualTo("desktop");
    assertThat(merged.getLeadInitialProcessedAt()).isEqualTo(processedAt);
  }

  @Test
  void changingExternalInvalidStageBackToValidRestoresLead() {
    SheetSource source = new SheetSource(7L, "sheet-only", "table_only");
    Customer incoming = new Customer();
    incoming.setPhone("13800000000");
    incoming.setCustomerStage("已到店体验");
    Customer existing = new Customer();
    existing.setPhone(incoming.getPhone());
    existing.setLeadInvalid(true);
    existing.setLeadInitialProcessedAt(LocalDateTime.now().minusHours(2));
    existing.setLeadInitialProcessedBy("企业微信同步");
    existing.setLeadRetainedUntil(LocalDateTime.now().plusHours(22));
    FieldMappingResolver resolver = mock(FieldMappingResolver.class);
    CustomerRepository repository = mock(CustomerRepository.class);
    CustomerMergeEngine merge = mock(CustomerMergeEngine.class);
    scheduler = scheduler(resolver, repository, mock(CustomerCacheManager.class), merge);
    when(resolver.mapRowResult(eq("table_only"), any(SheetRow.class)))
        .thenReturn(new FieldMappingResult(incoming,
            new TagExchangeResult(Map.of(), List.of(), List.of()),
            java.util.Set.of("customerStage")));
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), eq(100)))
        .thenReturn(List.of(new SheetRow("row-1", Map.of("phone", "13800000000"))));
    when(repository.findByPhone("13800000000"))
        .thenReturn(Optional.of(existing), Optional.of(incoming));
    when(merge.merge(incoming, existing)).thenReturn(incoming);

    scheduler.runOne(source);

    assertThat(incoming.isLeadInvalid()).isFalse();
    assertThat(incoming.getLeadInitialProcessedAt()).isNull();
    assertThat(incoming.getLeadInitialProcessedBy()).isNull();
    assertThat(incoming.getLeadRetainedUntil()).isNull();
  }

  @Test
  void unrelatedExternalFieldDoesNotChangeLeadValidity() {
    SheetSource source = new SheetSource(7L, "sheet-only", "table_only");
    Customer incoming = new Customer();
    incoming.setPhone("13800000000");
    incoming.setLeadInvalid(true);
    Customer existing = new Customer();
    existing.setPhone(incoming.getPhone());
    existing.setLeadInvalid(true);
    FieldMappingResolver resolver = mock(FieldMappingResolver.class);
    CustomerRepository repository = mock(CustomerRepository.class);
    CustomerMergeEngine merge = mock(CustomerMergeEngine.class);
    scheduler = scheduler(resolver, repository, mock(CustomerCacheManager.class), merge);
    when(resolver.mapRowResult(eq("table_only"), any(SheetRow.class)))
        .thenReturn(new FieldMappingResult(incoming,
            new TagExchangeResult(Map.of(), List.of(), List.of()),
            java.util.Set.of("nickname")));
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), eq(100)))
        .thenReturn(List.of(new SheetRow("row-1", Map.of("phone", "13800000000"))));
    when(repository.findByPhone("13800000000"))
        .thenReturn(Optional.of(existing), Optional.of(incoming));
    when(merge.merge(incoming, existing)).thenReturn(incoming);

    scheduler.runOne(source);

    assertThat(incoming.isLeadInvalid()).isTrue();
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
    verify(datasourceRepository, never()).saveSuccessfulSync(eq("table_only"), any(LocalDateTime.class));
  }

  @Test
  void failureUsesMappedPhoneWhenSourceColumnHasAnotherName() {
    SheetSource source = new SheetSource(7L, "sheet-only", "table_only");
    Customer incoming = new Customer();
    incoming.setPhone("13800000000");
    TagExchangeResult exchange = new TagExchangeResult(Map.of(), List.of(), List.of());
    FieldMappingResolver resolver = mock(FieldMappingResolver.class);
    CustomerRepository repository = mock(CustomerRepository.class);
    CustomerMergeEngine merge = mock(CustomerMergeEngine.class);
    SyncFailureRepository failures = mock(SyncFailureRepository.class);
    scheduler = scheduler(resolver, repository, mock(CustomerCacheManager.class), merge, failures);
    when(resolver.mapRowResult(eq("table_only"), any(SheetRow.class)))
        .thenReturn(new FieldMappingResult(incoming, exchange));
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), eq(100)))
        .thenReturn(List.of(new SheetRow("row-1", Map.of("手机号码", "13800000000"))));
    when(repository.findByPhone("13800000000")).thenReturn(java.util.Optional.empty());
    when(merge.merge(incoming, null)).thenReturn(incoming);
    doThrow(new IllegalStateException("projection failed"))
        .when(repository).upsert(incoming, exchange, TagExchangeSourceType.EXTERNAL_SYNC, "row-1");

    scheduler.runOne(source);

    verify(failures).record(eq("table_only"), eq("row-1"), eq("13800000000"),
        eq("projection failed"), any());
  }

  @Test
  void failedRowDoesNotAdvanceIncrementalWatermarkAndIsRequestedAgain() {
    SheetSource source = new SheetSource(7L, "sheet-only", "table_only");
    Customer incoming = new Customer();
    incoming.setPhone("13800000000");
    FieldMappingResolver resolver = mock(FieldMappingResolver.class);
    CustomerRepository repository = mock(CustomerRepository.class);
    CustomerMergeEngine merge = mock(CustomerMergeEngine.class);
    SyncFailureRepository failures = mock(SyncFailureRepository.class);
    scheduler = scheduler(resolver, repository, mock(CustomerCacheManager.class), merge, failures);
    TagExchangeResult exchange = new TagExchangeResult(Map.of(), List.of(), List.of());
    when(resolver.mapRowResult(eq("table_only"), any(SheetRow.class)))
        .thenReturn(new FieldMappingResult(incoming, exchange));
    when(sheetClient.fetchIncrementalRows(any(), any(LocalDateTime.class), eq(100)))
        .thenReturn(List.of(new SheetRow("row-1", Map.of("phone", "13800000000"))));
    when(repository.findByPhone("13800000000")).thenReturn(java.util.Optional.empty());
    when(merge.merge(incoming, null)).thenReturn(incoming);
    doThrow(new IllegalStateException("database down"))
        .when(repository).upsert(incoming, exchange, TagExchangeSourceType.EXTERNAL_SYNC, "row-1");

    scheduler.runOne(source);
    scheduler.runOne(source);

    org.mockito.ArgumentCaptor<LocalDateTime> modifiedAfter =
        org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
    verify(sheetClient, org.mockito.Mockito.times(2))
        .fetchIncrementalRows(eq(source), modifiedAfter.capture(), eq(100));
    assertThat(java.time.Duration.between(modifiedAfter.getAllValues().get(0),
        modifiedAfter.getAllValues().get(1)).abs()).isLessThan(java.time.Duration.ofSeconds(5));
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
