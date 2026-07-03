package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.sync.CustomerSyncScheduler;
import com.privateflow.modules.customer.sync.SheetClient;
import com.privateflow.modules.customer.sync.SheetRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class DatasourceAdminServiceTest {

  private DatasourceAdminRepository repository;
  private SheetClient sheetClient;
  private DatasourceAdminService service;

  @BeforeEach
  void setUp() {
    repository = mock(DatasourceAdminRepository.class);
    sheetClient = mock(SheetClient.class);
    service = new DatasourceAdminService(
        repository,
        mock(CustomerRepository.class),
        mock(CustomerSyncScheduler.class),
        sheetClient,
        mock(ApplicationEventPublisher.class),
        mock(WsPushService.class),
        new ObjectMapper());
  }

  @Test
  void createRejectsDuplicateDatasourceNameBeforeDatabaseConstraint() {
    when(repository.nameExists("duplicate-source", null)).thenReturn(true);

    assertThatThrownBy(() -> service.create(new DatasourceRequest("duplicate-source", "sheet-1", "source_table", "")))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ApiErrorCodes.BAD_REQUEST);
  }

  @Test
  void compareMappingsReturnsStructuredDiffAgainstLatestSnapshot() throws Exception {
    Datasource datasource = datasource();
    List<FieldMappingDto> current = List.of(
        new FieldMappingDto(1L, "phone", "phone", true),
        new FieldMappingDto(2L, "nickname", "nickname", true),
        new FieldMappingDto(3L, "stage", "customerStage", false));
    List<FieldMappingDto> baseline = List.of(
        new FieldMappingDto(10L, "phone", "phone", true),
        new FieldMappingDto(11L, "nickname", "customerStage", true),
        new FieldMappingDto(12L, "legacy", "leadType", true));
    when(repository.find(7L)).thenReturn(Optional.of(datasource));
    when(repository.mappings("source_table")).thenReturn(current);
    when(repository.latestMappingSnapshot(7L)).thenReturn(Optional.of(new DatasourceAdminRepository.MappingSnapshot(
        3,
        new ObjectMapper().writeValueAsString(baseline),
        baseline.size(),
        "admin",
        "snapshot",
        LocalDateTime.now())));

    Map<String, Object> result = service.compareMappings(7L);

    assertThat(result).containsKeys("summary", "diff", "baselineVersion");
    Map<?, ?> summary = (Map<?, ?>) result.get("summary");
    assertThat(summary.get("added")).isEqualTo(1);
    assertThat(summary.get("removed")).isEqualTo(1);
    assertThat(summary.get("changed")).isEqualTo(1);
    assertThat(summary.get("unchanged")).isEqualTo(1);
  }

  @Test
  void columnsUseSheetRowsAndSavedMappings() {
    Datasource datasource = datasource();
    when(repository.find(7L)).thenReturn(Optional.of(datasource));
    when(sheetClient.fetchIncrementalRows(eq("source_table"), eq(LocalDateTime.of(1970, 1, 1, 0, 0)), eq(20)))
        .thenReturn(List.of(new SheetRow("row-1", Map.of("phone", "13900000000", "nickname", "Alice"))));
    when(repository.mappings("source_table")).thenReturn(List.of(new FieldMappingDto(1L, "phone", "phone", true)));

    Map<String, Object> result = service.columns(7L);

    assertThat(result).containsEntry("source", "SHEET_SAMPLE").containsEntry("fetchStatus", "OK");
    assertThat((List<?>) result.get("columns")).hasSize(2);
  }

  @Test
  void columnsFallBackToMappingsWhenSheetClientUnavailable() {
    Datasource datasource = datasource();
    when(repository.find(7L)).thenReturn(Optional.of(datasource));
    when(sheetClient.fetchIncrementalRows(eq("source_table"), eq(LocalDateTime.of(1970, 1, 1, 0, 0)), eq(20)))
        .thenThrow(new IllegalStateException("not configured"));
    when(repository.mappings("source_table")).thenReturn(List.of(new FieldMappingDto(1L, "phone", "phone", true)));

    Map<String, Object> result = service.columns(7L);

    assertThat(result).containsEntry("source", "MAPPING_CONFIG").containsEntry("fetchStatus", "UNAVAILABLE");
    assertThat((List<?>) result.get("columns")).hasSize(1);
  }

  @Test
  void importLogsReadPersistedRepositoryRows() {
    when(repository.importLogs(50)).thenReturn(List.of(Map.of("fileName", "acceptance.csv")));
    when(repository.importLogCount()).thenReturn(1L);

    Map<String, Object> result = service.importLogs();

    assertThat(result).containsEntry("total", 1L).containsEntry("limit", 50);
    assertThat((List<?>) result.get("logs")).hasSize(1);
    verify(repository).importLogs(50);
  }

  @Test
  void saveMappingsRejectsDuplicateEnabledTargetFields() {
    when(repository.find(7L)).thenReturn(Optional.of(datasource()));

    MappingSaveRequest request = new MappingSaveRequest(List.of(
        new FieldMappingDto(null, "phone_a", "phone", true),
        new FieldMappingDto(null, "phone_b", "phone", true)));

    assertThatThrownBy(() -> service.saveMappings(7L, request))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ApiErrorCodes.CONFLICT);
  }

  private Datasource datasource() {
    return new Datasource(
        7L,
        "test-datasource",
        "sheet-1",
        "source_table",
        "",
        true,
        1,
        null,
        "OK",
        "admin",
        LocalDateTime.now(),
        LocalDateTime.now());
  }
}
