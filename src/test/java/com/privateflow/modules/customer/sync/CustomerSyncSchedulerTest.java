package com.privateflow.modules.customer.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.admin.DatasourceAdminRepository;
import com.privateflow.modules.customer.config.CustomerCacheProperties;
import com.privateflow.modules.customer.infra.CustomerCacheManager;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.service.CustomerMergeEngine;
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
}
