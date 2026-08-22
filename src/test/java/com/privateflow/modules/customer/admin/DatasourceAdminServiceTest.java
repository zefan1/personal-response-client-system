package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.sync.CustomerSyncScheduler;
import com.privateflow.modules.customer.sync.SheetClient;
import com.privateflow.modules.customer.sync.SheetRow;
import com.privateflow.modules.customer.sync.SheetSource;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetApiClient;
import java.time.Duration;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
import com.privateflow.modules.tags.TagExchangeUnmatchedValue;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

class DatasourceAdminServiceTest {

  private DatasourceAdminRepository repository;
  private CustomerRepository customerRepository;
  private SheetClient sheetClient;
  private CustomerSyncScheduler syncScheduler;
  private AuditLogger auditLogger;
  private TagExchangeService exchangeService;
  private DatasourceAdminService service;

  @BeforeEach
  void setUp() {
    repository = mock(DatasourceAdminRepository.class);
    customerRepository = mock(CustomerRepository.class);
    sheetClient = mock(SheetClient.class);
    syncScheduler = mock(CustomerSyncScheduler.class);
    auditLogger = mock(AuditLogger.class);
    exchangeService = mock(TagExchangeService.class);
    service = new DatasourceAdminService(
        repository,
        customerRepository,
        syncScheduler,
        sheetClient,
        mock(ApplicationEventPublisher.class),
        mock(WsPushService.class),
        new ObjectMapper(),
        auditLogger,
        exchangeService);
  }

  @Test
  void createDatasourceWritesAuditLog() {
    Datasource datasource = datasource();
    when(repository.nameExists("test-datasource", null)).thenReturn(false);
    when(repository.create(any(), anyString())).thenReturn(7L);
    when(repository.find(7L)).thenReturn(Optional.of(datasource));

    service.create(new DatasourceRequest("test-datasource", "sheet-1", "source_table", ""));

    verify(auditLogger).log(eq("DATASOURCE_CREATE"), anyString(), eq("datasource"), eq("7"), anyString());
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
    Map<String, String> sheetValues = new LinkedHashMap<>();
    sheetValues.put("nickname", "Alice");
    sheetValues.put("phone", "13900000000");
    when(sheetClient.fetchIncrementalRows(eq(new SheetSource(7L, "sheet-1", "source_table")), eq(LocalDateTime.of(1970, 1, 1, 0, 0)), eq(20)))
        .thenReturn(List.of(new SheetRow("row-1", sheetValues)));
    when(repository.mappings("source_table")).thenReturn(List.of(new FieldMappingDto(1L, "phone", "phone", true)));

    Map<String, Object> result = service.columns(7L);

    assertThat(result).containsEntry("source", "SHEET_SAMPLE").containsEntry("fetchStatus", "OK")
        .containsEntry("schemaReadable", true);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> columns = (List<Map<String, Object>>) result.get("columns");
    assertThat(columns).extracting(item -> item.get("name"))
        .containsExactly((Object) "nickname", "phone");
  }

  @Test
  void columnsFallBackToMappingsWhenSheetClientUnavailable() {
    Datasource datasource = datasource();
    when(repository.find(7L)).thenReturn(Optional.of(datasource));
    when(sheetClient.fetchIncrementalRows(eq(new SheetSource(7L, "sheet-1", "source_table")), eq(LocalDateTime.of(1970, 1, 1, 0, 0)), eq(20)))
        .thenThrow(new IllegalStateException("not configured"));
    when(repository.mappings("source_table")).thenReturn(List.of(new FieldMappingDto(1L, "phone", "phone", true)));

    Map<String, Object> result = service.columns(7L);

    assertThat(result).containsEntry("source", "MAPPING_CONFIG").containsEntry("fetchStatus", "UNAVAILABLE")
        .containsEntry("schemaReadable", false);
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
  void syncReportsConflictWhenGlobalSyncLockIsOccupied() {
    when(repository.find(7L)).thenReturn(Optional.of(datasource()));
    when(syncScheduler.tryStartOneAsync(any(SheetSource.class))).thenReturn(false);

    assertThatThrownBy(() -> service.sync(7L))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ApiErrorCodes.CONFLICT);

    verify(syncScheduler).tryStartOneAsync(new SheetSource(7L, "sheet-1", "source_table"));
    verify(auditLogger, never()).log(eq("DATASOURCE_SYNC_START"), anyString(), eq("datasource"), anyString(), anyString());
  }

  @AfterEach
  void clearAuth() {
    AuthContext.clear();
  }

  @Test
  void syncStatusExcludesLegacyAndApiOwnedDatasources() {
    Datasource managed = new Datasource(
        45L, "分配表", "assignment-doc", "ASSIGNMENT:q979lj",
        "SYSTEM_MANAGED_SMART_SHEET:ASSIGNMENT", true, 10, null, "ERROR",
        "system", null, null);
    Datasource legacy = new Datasource(
        47L, "PrivateDomainAssistant-API", "primary-doc", "th1zyU",
        "Enterprise WeCom Smart Sheet API datasource", true, 19, null, "OK",
        "system", null, null);
    Datasource imported = new Datasource(
        3L, "历史导入", "sheet-neg", "negative_20260709092920",
        "negative", false, 0, null, "OK", "admin", null, null);
    when(repository.list()).thenReturn(List.of(managed, legacy, imported));
    when(repository.unresolvedFailures("ASSIGNMENT:q979lj")).thenReturn(List.of("手机号为空"));

    Map<String, Object> result = service.syncStatus();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
    assertThat(items).hasSize(1);
    assertThat(items.get(0)).containsEntry("datasourceId", 45L)
        .containsEntry("sourceTable", "ASSIGNMENT:q979lj");
    verify(repository, never()).unresolvedFailures("th1zyU");
  }

  @Test
  void adminCanResolveOnlyConfirmedHistoricalFailuresWithoutResyncingTheTable() {
    AuthContext.set(new AuthUser("admin", "管理员", Role.ADMIN, null));
    Datasource managed = new Datasource(
        45L, "分配表", "assignment-doc", "ASSIGNMENT:q979lj",
        "SYSTEM_MANAGED_SMART_SHEET:ASSIGNMENT", true, 10, null, "ERROR",
        "system", null, null);
    when(repository.find(45L)).thenReturn(Optional.of(managed));
    when(repository.resolveFailuresBefore(eq("ASSIGNMENT:q979lj"), any(LocalDateTime.class))).thenReturn(345);

    Map<String, Object> result = service.resolveHistoricalFailures(45L,
        new HistoricalSyncFailureResolveRequest(
            "ASSIGNMENT:q979lj", LocalDateTime.now().minusMinutes(1), "映射修复后归档历史失败"));

    assertThat(result).containsEntry("resolvedCount", 345).containsEntry("sourceTable", "ASSIGNMENT:q979lj");
    verify(repository).resolveFailuresBefore(eq("ASSIGNMENT:q979lj"), any(LocalDateTime.class));
    verify(auditLogger).log(eq("DATASOURCE_SYNC_FAILURES_RESOLVE"), eq("admin"), eq("datasource"), eq("45"), anyString());
  }

  @Test
  void historicalFailureResolveRejectsMismatchedConfirmation() {
    AuthContext.set(new AuthUser("admin", "管理员", Role.ADMIN, null));
    Datasource managed = new Datasource(
        45L, "分配表", "assignment-doc", "ASSIGNMENT:q979lj",
        "SYSTEM_MANAGED_SMART_SHEET:ASSIGNMENT", true, 10, null, "ERROR",
        "system", null, null);
    when(repository.find(45L)).thenReturn(Optional.of(managed));

    assertThatThrownBy(() -> service.resolveHistoricalFailures(45L,
        new HistoricalSyncFailureResolveRequest("th1zyU", LocalDateTime.now().minusMinutes(1), "归档")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("确认表名");
    verify(repository, never()).resolveFailuresBefore(anyString(), any(LocalDateTime.class));
  }

  @Test
  void csvImportKeepsRowAndReportsUnmatchedTagValues() {
    when(customerRepository.findByPhone("13800000000")).thenReturn(Optional.empty());
    TagExchangeUnmatchedValue unmatched = new TagExchangeUnmatchedValue(
        "bodyConcerns",
        "漏尿,未知关注",
        List.of("未知关注"),
        1L,
        TagExchangeSourceType.CSV_IMPORT,
        "2");
    TagExchangeResult exchange = new TagExchangeResult(
        Map.of("bodyConcerns", "URINE_LEAKAGE"),
        List.of(),
        List.of(unmatched));
    when(exchangeService.prepareInbound(
        eq(TagExchangeSourceType.CSV_IMPORT),
        eq("2"),
        any(Map.class))).thenReturn(exchange);

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "customers.csv",
        "text/csv",
        "phone,nickname,bodyConcerns\n13800000000,Alice,漏尿,未知关注\n".getBytes());

    CsvImportResult result = service.importCsv(file);

    assertThat(result.created()).isEqualTo(1);
    assertThat(result.skipped()).isZero();
    assertThat(result.unmatchedCount()).isEqualTo(1);
    assertThat(result.unmatchedRows()).containsExactly(2);
    verify(customerRepository).upsert(
        any(),
        eq(exchange),
        eq(TagExchangeSourceType.CSV_IMPORT),
        eq("2"));
  }

  @Test
  void customerFieldDictionaryReturnsChineseBusinessLabels() {
    Map<String, Object> result = service.customerFields();
    @SuppressWarnings("unchecked")
    List<CustomerFieldDto> fields = (List<CustomerFieldDto>) result.get("fields");

    assertThat(fields).extracting(CustomerFieldDto::label)
        .contains(
            "客户昵称", "意向等级", "下次跟进时间", "预约项目", "是否到店", "分配管家",
            "备注", "客户档案摘要", "第一次追踪捕捉", "第二次追踪捕捉", "第三次追踪捕捉",
            "客户姓名", "到店体验项目", "成交金额");
    assertThat(fields).allSatisfy(field -> assertThat(field.label()).doesNotMatch("^[A-Za-z][A-Za-z0-9]*$"));
  }

  @Test
  void customerFieldDictionaryUsesTheConfirmedLeadAndAssignmentLabels() {
    @SuppressWarnings("unchecked")
    List<CustomerFieldDto> fields = (List<CustomerFieldDto>) service.customerFields().get("fields");

    assertThat(fields).extracting(CustomerFieldDto::label)
        .contains("微信号", "客资类型", "留资方式", "平台留资时间", "分配日期")
        .doesNotContain("留资类型", "客户顾虑");
  }

  @Test
  void saveMappingsReturnsEnabledMappingCount() {
    when(repository.find(7L)).thenReturn(Optional.of(datasource()));
    when(sheetClient.fetchIncrementalRows(eq(new SheetSource(7L, "sheet-1", "source_table")), eq(LocalDateTime.of(1970, 1, 1, 0, 0)), eq(20)))
        .thenReturn(List.of(new SheetRow("row-1", Map.of("联系方式", "13900000000"))));
    when(repository.createMappingVersion(eq(7L), anyString(), eq(1), anyString(), anyString())).thenReturn(3);

    Map<String, Object> result = service.saveMappings(7L, new MappingSaveRequest(List.of(
        new FieldMappingDto(null, "联系方式", "phone", true),
        new FieldMappingDto(null, "客户昵称", "nickname", false))));

    assertThat(result).containsEntry("mappingCount", 1).containsEntry("version", 3);
    verify(repository).replaceMappings("source_table", List.of(
        new FieldMappingDto(null, "联系方式", "phone", true),
        new FieldMappingDto(null, "客户昵称", "nickname", false)));
  }

  @Test
  void saveMappingsAcceptsAnyRemoteColumnBoundToPhone() {
    when(repository.find(7L)).thenReturn(Optional.of(datasource()));
    when(sheetClient.fetchIncrementalRows(eq(new SheetSource(7L, "sheet-1", "source_table")), eq(LocalDateTime.of(1970, 1, 1, 0, 0)), eq(20)))
        .thenReturn(List.of(new SheetRow("row-1", Map.of("客户联系电话", "13900000000"))));
    when(repository.createMappingVersion(eq(7L), anyString(), eq(1), anyString(), anyString())).thenReturn(4);

    Map<String, Object> result = service.saveMappings(7L, new MappingSaveRequest(List.of(
        new FieldMappingDto(null, "客户联系电话", "phone", true))));

    assertThat(result).containsEntry("mappingCount", 1).containsEntry("version", 4);
    verify(repository).replaceMappings("source_table", List.of(
        new FieldMappingDto(null, "客户联系电话", "phone", true)));
  }

  @Test
  void saveMappingsRejectsEnabledFieldsThatAreNoLongerInTheRemoteSchema() {
    when(repository.find(7L)).thenReturn(Optional.of(datasource()));
    when(sheetClient.fetchIncrementalRows(eq(new SheetSource(7L, "sheet-1", "source_table")), eq(LocalDateTime.of(1970, 1, 1, 0, 0)), eq(20)))
        .thenReturn(List.of(new SheetRow("row-1", Map.of("phone", "13900000000"))));

    assertThatThrownBy(() -> service.saveMappings(7L, new MappingSaveRequest(List.of(
        new FieldMappingDto(null, "legacy_phone", "phone", true)))))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ApiErrorCodes.CONFLICT);

    verify(repository, never()).replaceMappings(anyString(), anyList());
  }

  @Test
  void saveMappingsRejectsOverwriteWhenSourceSchemaUnavailable() {
    when(repository.find(7L)).thenReturn(Optional.of(datasource()));
    when(sheetClient.fetchIncrementalRows(eq(new SheetSource(7L, "sheet-1", "source_table")), eq(LocalDateTime.of(1970, 1, 1, 0, 0)), eq(20)))
        .thenThrow(new IllegalStateException("relay network request failed"));

    assertThatThrownBy(() -> service.saveMappings(7L, new MappingSaveRequest(List.of(
        new FieldMappingDto(null, "联系方式", "phone", true)))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("已拒绝保存映射")
        .hasMessageContaining("relay network request failed");

    verify(repository, never()).replaceMappings(anyString(), anyList());
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

  @Test
  void managedSmartSheetMappingRequiresPhoneMapping() throws Exception {
    Datasource arrivalDatasource = new Datasource(7L, "到店表", "arrival-doc", "ARRIVAL:arrival-sheet",
        "SYSTEM_MANAGED_SMART_SHEET:ARRIVAL", true, 0, null, "OK", "system", null, null);
    WecomSmartSheetApiClient smartSheetApiClient = mock(WecomSmartSheetApiClient.class);
    when(smartSheetApiClient.post(eq("get_fields"), any(), any(Duration.class)))
        .thenReturn(new ObjectMapper().readTree("{\"fields\":[{\"field_title\":\"姓名\"}]}"));
    DatasourceAdminService managedService = new DatasourceAdminService(
        repository, customerRepository, mock(CustomerSyncScheduler.class), sheetClient,
        mock(ApplicationEventPublisher.class), mock(WsPushService.class), new ObjectMapper(),
        auditLogger, exchangeService, smartSheetApiClient, null,
        new AuxiliarySmartSheetTargets("", "", "", "", "arrival-doc", "arrival-sheet", "arrival-view", ""));
    when(repository.find(7L)).thenReturn(Optional.of(arrivalDatasource));

    assertThatThrownBy(() -> managedService.saveMappings(7L,
        new MappingSaveRequest(List.of(new FieldMappingDto(0L, "姓名", "nickname", true)))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("实际代表手机号的列")
        .hasMessageContaining("手机号");
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
