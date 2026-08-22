package com.privateflow.modules.customer.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerMasterFieldCatalog;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.sync.CustomerSyncScheduler;
import com.privateflow.modules.customer.sync.SheetClient;
import com.privateflow.modules.customer.sync.SheetRow;
import com.privateflow.modules.customer.sync.SheetSource;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetApiClient;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetFieldMetadata;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DatasourceAdminService {

  private static final String FIELD_MAPPING_CONFIG_KEY = "datasource.field_mappings";
  private static final String CONNECTIONS_CONFIG_KEY = "datasource.connections";
  private static final int IMPORT_MAX_ROWS = 5000;
  private static final Set<String> SYSTEM_FIELDS = Set.of("class", "id", "version", "createdAt", "updatedAt", "syncedAt", "sourceRowId");
  private final DatasourceAdminRepository repository;
  private final CustomerRepository customerRepository;
  private final CustomerSyncScheduler syncScheduler;
  private final SheetClient sheetClient;
  private final ApplicationEventPublisher eventPublisher;
  private final WsPushService wsPushService;
  private final ObjectMapper objectMapper;
  private final AuditLogger auditLogger;
  private final TagExchangeService exchangeService;
  private final WecomSmartSheetApiClient smartSheetApiClient;
  private final WecomSmartSheetConfig smartSheetConfig;
  private final AuxiliarySmartSheetTargets auxiliarySmartSheetTargets;

  @Autowired
  public DatasourceAdminService(
      DatasourceAdminRepository repository,
      CustomerRepository customerRepository,
      CustomerSyncScheduler syncScheduler,
      SheetClient sheetClient,
      ApplicationEventPublisher eventPublisher,
      WsPushService wsPushService,
      ObjectMapper objectMapper,
      AuditLogger auditLogger,
      TagExchangeService exchangeService,
      WecomSmartSheetApiClient smartSheetApiClient,
      WecomSmartSheetConfig smartSheetConfig,
      AuxiliarySmartSheetTargets auxiliarySmartSheetTargets) {
    this.repository = repository;
    this.customerRepository = customerRepository;
    this.syncScheduler = syncScheduler;
    this.sheetClient = sheetClient;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
    this.objectMapper = objectMapper;
    this.auditLogger = auditLogger;
    this.exchangeService = exchangeService;
    this.smartSheetApiClient = smartSheetApiClient;
    this.smartSheetConfig = smartSheetConfig;
    this.auxiliarySmartSheetTargets = auxiliarySmartSheetTargets;
  }

  public DatasourceAdminService(
      DatasourceAdminRepository repository,
      CustomerRepository customerRepository,
      CustomerSyncScheduler syncScheduler,
      SheetClient sheetClient,
      ApplicationEventPublisher eventPublisher,
      WsPushService wsPushService,
      ObjectMapper objectMapper,
      AuditLogger auditLogger,
      TagExchangeService exchangeService) {
    this(
        repository,
        customerRepository,
        syncScheduler,
        sheetClient,
        eventPublisher,
        wsPushService,
        objectMapper,
        auditLogger,
        exchangeService,
        null,
        null,
        null);
  }

  public DatasourceAdminService(
      DatasourceAdminRepository repository,
      CustomerRepository customerRepository,
      CustomerSyncScheduler syncScheduler,
      SheetClient sheetClient,
      ApplicationEventPublisher eventPublisher,
      WsPushService wsPushService,
      ObjectMapper objectMapper,
      AuditLogger auditLogger) {
    this(
        repository,
        customerRepository,
        syncScheduler,
        sheetClient,
        eventPublisher,
        wsPushService,
        objectMapper,
        auditLogger,
        null,
        null,
        null,
        null);
  }

  public Map<String, Object> list() {
    ensureManagedSmartSheetDatasources();
    List<Datasource> datasources = repository.list().stream()
        .filter(datasource -> datasource.description() != null
            && datasource.description().startsWith("SYSTEM_MANAGED_SMART_SHEET:"))
        .toList();
    return Map.of("datasources", datasources, "total", datasources.size());
  }

  private void ensureManagedSmartSheetDatasources() {
    if (smartSheetConfig != null && !smartSheetConfig.documentId().isBlank() && !smartSheetConfig.sheetId().isBlank()) {
      repository.ensureManagedSmartSheetDatasource("PRIMARY", smartSheetConfig.documentId(), smartSheetConfig.sheetId());
    }
    if (auxiliarySmartSheetTargets != null) {
      auxiliarySmartSheetTargets.assignment().ifPresent(target ->
          repository.ensureManagedSmartSheetDatasource("ASSIGNMENT", target.documentId(), target.sheetId()));
      auxiliarySmartSheetTargets.arrival().ifPresent(target ->
          repository.ensureManagedSmartSheetDatasource("ARRIVAL", target.documentId(), target.sheetId()));
    }
  }

  public Datasource create(DatasourceRequest request) {
    validateDatasource(request, true, null);
    long id = repository.create(request, AuthContext.username());
    publish(CONNECTIONS_CONFIG_KEY);
    Datasource saved = repository.find(id).orElseThrow();
    audit("DATASOURCE_CREATE", saved, datasourceDetail(saved));
    return saved;
  }

  public Datasource update(long id, DatasourceRequest request) {
    Datasource existing = repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    validateDatasource(request, false, id);
    repository.update(id, request);
    publish(CONNECTIONS_CONFIG_KEY);
    Datasource saved = repository.find(id).orElseThrow();
    Map<String, Object> detail = datasourceDetail(saved);
    detail.put("previousSheetId", existing.sheetId());
    detail.put("previousSourceTable", existing.sourceTable());
    audit("DATASOURCE_UPDATE", saved, detail);
    return saved;
  }

  @Transactional
  public Map<String, Object> delete(long id) {
    Datasource datasource = repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    int deletedMappings = repository.deleteMappings(datasource.sourceTable());
    repository.delete(id);
    publish(CONNECTIONS_CONFIG_KEY);
    publish(FIELD_MAPPING_CONFIG_KEY);
    Map<String, Object> detail = datasourceDetail(datasource);
    detail.put("deletedMappings", deletedMappings);
    audit("DATASOURCE_DELETE", datasource, detail);
    return Map.of("deletedMappings", deletedMappings);
  }

  public Datasource toggle(long id, boolean enabled) {
    Datasource existing = repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    repository.toggle(id, enabled);
    publish(CONNECTIONS_CONFIG_KEY);
    Datasource saved = repository.find(id).orElseThrow();
    Map<String, Object> detail = datasourceDetail(saved);
    detail.put("enabledBefore", existing.enabled());
    detail.put("enabledAfter", enabled);
    audit("DATASOURCE_TOGGLE", saved, detail);
    return saved;
  }

  public Map<String, Object> replace(long id, DatasourceReplaceRequest request) {
    if (request.sheetId() == null || request.sheetId().isBlank()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "sheetId is required");
    }
    repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    String oldSheetId = repository.replace(id, request.sheetId().trim());
    publish(CONNECTIONS_CONFIG_KEY);
    Datasource datasource = repository.find(id).orElse(null);
    audit("DATASOURCE_REPLACE_SHEET", datasource == null ? new Datasource(id, "", request.sheetId().trim(), "", "", true, 0, null, "", AuthContext.username(), null, null) : datasource,
        Map.of("oldSheetId", oldSheetId, "newSheetId", request.sheetId().trim(), "mappingPreserved", true));
    return Map.of("oldSheetId", oldSheetId, "newSheetId", request.sheetId().trim(), "mappingPreserved", true);
  }

  public Map<String, Object> mappings(long id) {
    Datasource datasource = repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    return Map.of(
        "datasourceId", id,
        "sourceTable", datasource.sourceTable(),
        "mappings", repository.mappings(datasource.sourceTable()),
        "currentVersion", repository.currentMappingVersion(id));
  }

  @Transactional
  public Map<String, Object> saveMappings(long id, MappingSaveRequest request) {
    Datasource datasource = repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    List<FieldMappingDto> mappings = validateMappings(request);
    validateManagedSmartSheetMapping(datasource, mappings);
    ExternalSchema schema = requireReadableExternalSchema(datasource);
    validateSourceColumns(schema, mappings);
    repository.replaceMappings(datasource.sourceTable(), mappings);
    int enabledMappingCount = (int) mappings.stream().filter(FieldMappingDto::enabled).count();
    int version = repository.createMappingVersion(id, toJson(mappings), enabledMappingCount,
        "replace mappings: " + enabledMappingCount, AuthContext.username());
    publish(FIELD_MAPPING_CONFIG_KEY);
    audit("DATASOURCE_MAPPING_SAVE", datasource, Map.of(
        "datasourceId", datasource.id(),
        "sourceTable", datasource.sourceTable(),
        "mappingCount", enabledMappingCount,
        "version", version));
    return Map.of("mappingCount", enabledMappingCount, "version", version);
  }

  public Map<String, Object> mappingVersions(long id) {
    repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    return Map.of("versions", repository.mappingVersions(id));
  }

  public Map<String, Object> compareMappings(long id) {
    Datasource datasource = repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    List<FieldMappingDto> current = repository.mappings(datasource.sourceTable());
    DatasourceAdminRepository.MappingSnapshot snapshot = repository.latestMappingSnapshot(id).orElse(null);
    List<FieldMappingDto> baseline = snapshot == null ? List.of() : fromJson(snapshot.mappingsJson());
    Map<String, FieldMappingDto> currentBySource = bySourceField(current);
    Map<String, FieldMappingDto> baselineBySource = bySourceField(baseline);
    List<Map<String, Object>> added = new ArrayList<>();
    List<Map<String, Object>> removed = new ArrayList<>();
    List<Map<String, Object>> changed = new ArrayList<>();
    List<Map<String, Object>> unchanged = new ArrayList<>();
    for (Map.Entry<String, FieldMappingDto> entry : currentBySource.entrySet()) {
      FieldMappingDto previous = baselineBySource.get(entry.getKey());
      FieldMappingDto now = entry.getValue();
      if (previous == null) {
        added.add(mappingItem(now));
      } else if (!sameMapping(previous, now)) {
        changed.add(Map.of("sourceField", now.sourceField(), "before", mappingItem(previous), "after", mappingItem(now)));
      } else {
        unchanged.add(mappingItem(now));
      }
    }
    for (Map.Entry<String, FieldMappingDto> entry : baselineBySource.entrySet()) {
      if (!currentBySource.containsKey(entry.getKey())) {
        removed.add(mappingItem(entry.getValue()));
      }
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("datasourceId", id);
    result.put("sourceTable", datasource.sourceTable());
    result.put("baselineVersion", snapshot == null ? 0 : snapshot.version());
    result.put("baselineCreatedAt", snapshot == null ? null : snapshot.createdAt());
    result.put("summary", Map.of(
        "currentCount", current.size(),
        "baselineCount", baseline.size(),
        "added", added.size(),
        "removed", removed.size(),
        "changed", changed.size(),
        "unchanged", unchanged.size()));
    result.put("diff", Map.of("added", added, "removed", removed, "changed", changed, "unchanged", unchanged));
    return result;
  }

  @Transactional
  public Map<String, Object> restoreMappings(long id, MappingRestoreRequest request) {
    Datasource datasource = repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    String json = repository.mappingSnapshot(id, request.version())
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "mapping version not found"));
    List<FieldMappingDto> mappings = fromJson(json);
    validateManagedSmartSheetMapping(datasource, mappings);
    ExternalSchema schema = requireReadableExternalSchema(datasource);
    validateSourceColumns(schema, mappings);
    repository.replaceMappings(datasource.sourceTable(), mappings);
    int newVersion = repository.createMappingVersion(id, toJson(mappings), mappings.size(), "restore from version " + request.version(), AuthContext.username());
    publish(FIELD_MAPPING_CONFIG_KEY);
    audit("DATASOURCE_MAPPING_RESTORE", datasource, Map.of(
        "datasourceId", datasource.id(),
        "sourceTable", datasource.sourceTable(),
        "restoredVersion", request.version(),
        "newVersion", newVersion,
        "mappingCount", mappings.size()));
    return Map.of("restoredVersion", request.version(), "newVersion", newVersion, "mappingCount", mappings.size());
  }

  public Map<String, Object> columns(long id) {
    Datasource datasource = repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    LinkedHashSet<String> columnNames = new LinkedHashSet<>();
    ExternalSchema schema = readExternalSchema(datasource);
    columnNames.addAll(schema.columns());
    String source = schema.readable() ? schema.source() : "MAPPING_CONFIG";
    String fetchStatus = schema.readable() ? "OK" : "UNAVAILABLE";
    String fetchError = schema.error();
    List<FieldMappingDto> mappings = repository.mappings(datasource.sourceTable());
    Set<String> remoteColumns = new LinkedHashSet<>(schema.columns());
    for (FieldMappingDto mapping : mappings) {
      if (!remoteColumns.contains(mapping.sourceField())) {
        columnNames.add(mapping.sourceField());
      }
    }
    // Keep the order returned by WeCom. The fields endpoint follows the
    // Smart Sheet's left-to-right column order; sorting here makes the mapping
    // editor look random and disconnects it from the actual table layout.
    List<Map<String, Object>> columns = columnNames.stream()
        .map(column -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("name", column);
          boolean remotePresent = remoteColumns.contains(column);
          item.put("remotePresent", remotePresent);
          item.put("stale", !remotePresent && schema.readable());
          mappings.stream()
              .filter(mapping -> mapping.sourceField().equals(column))
              .findFirst()
              .ifPresent(mapping -> {
                item.put("mapped", true);
                item.put("targetField", mapping.targetField());
                item.put("enabled", mapping.enabled());
              });
          item.putIfAbsent("mapped", false);
          return item;
        })
        .toList();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("datasourceId", id);
    result.put("sourceTable", datasource.sourceTable());
    result.put("columns", columns);
    result.put("source", source);
    result.put("fetchStatus", fetchStatus);
    result.put("externalFetchAvailable", "OK".equals(fetchStatus));
    result.put("schemaReadable", schema.readable());
    result.put("fallback", !"OK".equals(fetchStatus) || "MAPPING_CONFIG".equals(source));
    if (fetchError != null && !fetchError.isBlank()) {
      result.put("fetchError", fetchError);
    }
    return result;
  }

  private ExternalSchema requireReadableExternalSchema(Datasource datasource) {
    ExternalSchema schema = readExternalSchema(datasource);
    if (!schema.readable()) {
      String detail = schema.error() == null || schema.error().isBlank() ? "未返回任何列名" : schema.error();
      throw new ApiException(ApiErrorCodes.CONFLICT,
          "无法读取企业微信表格列名，已拒绝保存映射以保护现有配置。请恢复表格连接后重新识别列名。原因：" + detail);
    }
    return schema;
  }

  private void validateSourceColumns(ExternalSchema schema, List<FieldMappingDto> mappings) {
    Set<String> available = new LinkedHashSet<>(schema.columns());
    List<String> stale = mappings.stream()
        .filter(FieldMappingDto::enabled)
        .map(FieldMappingDto::sourceField)
        .filter(source -> !available.contains(source))
        .distinct()
        .toList();
    if (!stale.isEmpty()) {
      throw new ApiException(ApiErrorCodes.CONFLICT,
          "以下映射字段已不在企业微信表格中，请先清除或改为真实列名：" + String.join("、", stale));
    }
  }

  private ExternalSchema readExternalSchema(Datasource datasource) {
    try {
      List<String> columns;
      AuxiliarySmartSheetTarget target = managedSmartSheetTarget(datasource);
      if (target != null) {
        columns = loadSmartSheetColumns(target);
        return new ExternalSchema(columns, "SMART_SHEET_SCHEMA", null);
      }
      List<SheetRow> rows = sheetClient.fetchIncrementalRows(
          sheetSource(datasource), LocalDateTime.of(1970, 1, 1, 0, 0), 20);
      LinkedHashSet<String> columnNames = new LinkedHashSet<>();
      if (rows != null) {
        for (SheetRow row : rows) {
          if (row != null && row.values() != null) {
            columnNames.addAll(row.values().keySet());
          }
        }
      }
      if (columnNames.isEmpty()) {
        return new ExternalSchema(List.of(), "SHEET_SAMPLE", "企业微信未返回可用列名");
      }
      return new ExternalSchema(List.copyOf(columnNames), "SHEET_SAMPLE", null);
    } catch (RuntimeException ex) {
      return new ExternalSchema(List.of(), "MAPPING_CONFIG", ex.getMessage());
    }
  }

  private record ExternalSchema(List<String> columns, String source, String error) {
    private boolean readable() {
      return error == null && !columns.isEmpty();
    }
  }

  private AuxiliarySmartSheetTarget managedSmartSheetTarget(Datasource datasource) {
    if (smartSheetConfig != null && datasource.sheetId().equals(smartSheetConfig.documentId())
        && datasource.sourceTable().equals(smartSheetConfig.sourceTable())) {
      return new AuxiliarySmartSheetTarget("PRIMARY", smartSheetConfig.documentId(), smartSheetConfig.sheetId(),
          smartSheetConfig.viewId(), smartSheetConfig.uniqueFieldTitle(), "");
    }
    if (auxiliarySmartSheetTargets == null) return null;
    return auxiliarySmartSheetTargets.assignment()
        .filter(target -> datasource.sourceTable().equals("ASSIGNMENT:" + target.sheetId()))
        .or(() -> auxiliarySmartSheetTargets.arrival()
            .filter(target -> datasource.sourceTable().equals("ARRIVAL:" + target.sheetId())))
        .orElse(null);
  }

  private void validateManagedSmartSheetMapping(Datasource datasource, List<FieldMappingDto> mappings) {
    AuxiliarySmartSheetTarget target = managedSmartSheetTarget(datasource);
    if (target == null) return;
    long phoneMappings = mappings.stream().filter(mapping -> mapping.enabled()
        && "phone".equals(mapping.targetField())).count();
    if (phoneMappings != 1) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST,
          "请将企业微信表格中实际代表手机号的列设置为系统内容“手机号”，系统将以唯一事实数据库的手机号查找客户");
    }
  }

  private List<String> loadSmartSheetColumns(AuxiliarySmartSheetTarget target) {
    if (smartSheetApiClient == null || target == null || !target.configured()) {
      throw new IllegalStateException("企业微信表格连接尚未就绪");
    }
    JsonNode fields = smartSheetApiClient.postForTarget("get_fields", Map.of(
        "docid", target.documentId(),
        "sheet_id", target.sheetId(),
        "view_id", target.viewId(),
        "offset", 0,
        "limit", 1000), Duration.ofSeconds(10), "PRIMARY".equals(target.role())).get("fields");
    if (fields == null || !fields.isArray()) {
      throw new IllegalStateException("企业微信未返回客户主表列名");
    }
    List<String> result = new ArrayList<>();
    for (JsonNode field : fields) {
      String title = WecomSmartSheetFieldMetadata.title(field);
      if (!title.isBlank()) {
        result.add(title);
      }
    }
    if (result.isEmpty()) {
      throw new IllegalStateException("客户主表没有可用列");
    }
    return result;
  }

  public Map<String, Object> customerFields() {
    List<CustomerFieldDto> fields = CustomerMasterFieldCatalog.fields().stream()
        .map(field -> new CustomerFieldDto(field.name(), field.label(), field.category()))
        .toList();
    return Map.of("fields", fields);
  }

  public Map<String, Object> syncStatus() {
    List<Map<String, Object>> items = repository.list().stream()
        // The admin console is scoped to the three managed Smart Sheets. Legacy
        // imports and API-owned migration rows must not alter the operator view.
        .filter(source -> source.description() != null
            && source.description().startsWith("SYSTEM_MANAGED_SMART_SHEET:"))
        .map(source -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("datasourceId", source.id());
          item.put("sourceTable", source.sourceTable());
          item.put("lastSyncAt", source.lastSyncAt());
          item.put("syncStatus", source.syncStatus());
          item.put("mappingCount", source.mappingCount());
          item.put("failures", repository.unresolvedFailures(source.sourceTable()));
          return item;
        })
        .toList();
    return Map.of("items", items);
  }

  public Map<String, Object> sync(long id) {
    return sync(id, false);
  }

  public Map<String, Object> sync(long id, boolean full) {
    Datasource datasource = repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    if (!datasource.enabled()) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "datasource is disabled");
    }
    boolean started = full
        ? syncScheduler.tryStartOneAsync(sheetSource(datasource), true)
        : syncScheduler.tryStartOneAsync(sheetSource(datasource));
    if (!started) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "datasource sync already running");
    }
    audit("DATASOURCE_SYNC_START", datasource, Map.of(
        "datasourceId", id,
        "sourceTable", datasource.sourceTable(),
        "mode", full ? "FULL" : "INCREMENTAL"));
    return Map.of("accepted", true, "datasourceId", id, "sourceTable", datasource.sourceTable(),
        "mode", full ? "FULL" : "INCREMENTAL");
  }

  @Transactional
  public Map<String, Object> resolveHistoricalFailures(long id, HistoricalSyncFailureResolveRequest request) {
    requireAdmin();
    if (request == null || request.before() == null || blank(request.reason()) || blank(request.confirmSourceTable())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请提供截止时间、确认表名和归档原因");
    }
    if (request.before().isAfter(LocalDateTime.now())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "归档截止时间不能晚于当前时间");
    }
    Datasource datasource = repository.find(id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    if (datasource.description() == null || !datasource.description().startsWith("SYSTEM_MANAGED_SMART_SHEET:")) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "只能归档系统管理的企业微信智能表格历史失败");
    }
    if (!datasource.sourceTable().equals(request.confirmSourceTable().trim())) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "确认表名与当前数据源不一致");
    }
    int resolved = repository.resolveFailuresBefore(datasource.sourceTable(), request.before());
    audit("DATASOURCE_SYNC_FAILURES_RESOLVE", datasource, Map.of(
        "sourceTable", datasource.sourceTable(),
        "before", request.before().toString(),
        "reason", request.reason().trim(),
        "resolvedCount", resolved));
    return Map.of(
        "datasourceId", datasource.id(),
        "sourceTable", datasource.sourceTable(),
        "resolvedCount", resolved,
        "before", request.before(),
        "message", "已归档截止时间前的历史同步失败记录；原始记录保留，不会重跑或写入企业微信表");
  }

  private SheetSource sheetSource(Datasource datasource) {
    return new SheetSource(datasource.id(), datasource.sheetId(), datasource.sourceTable());
  }

  public CsvImportResult importCsv(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "csv file is required");
    }
    CsvImportResult result = parseCsv(file);
    repository.logImport(file.getOriginalFilename(), result, AuthContext.username());
    auditLogger.log("DATASOURCE_CSV_IMPORT", AuthContext.username(), "datasource", "CSV_IMPORT", toJson(Map.of(
        "fileName", file.getOriginalFilename(),
        "totalRows", result.totalRows(),
        "created", result.created(),
        "updated", result.updated(),
        "skipped", result.skipped())));
    return result;
  }

  public Map<String, Object> importLogs() {
    int limit = 50;
    return Map.of("logs", repository.importLogs(limit), "total", repository.importLogCount(), "limit", limit);
  }

  private Map<String, FieldMappingDto> bySourceField(List<FieldMappingDto> mappings) {
    Map<String, FieldMappingDto> result = new LinkedHashMap<>();
    for (FieldMappingDto mapping : mappings) {
      result.put(mapping.sourceField(), mapping);
    }
    return result;
  }

  private boolean sameMapping(FieldMappingDto left, FieldMappingDto right) {
    return left.targetField().equals(right.targetField()) && left.enabled() == right.enabled();
  }

  private Map<String, Object> mappingItem(FieldMappingDto mapping) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", mapping.id());
    item.put("sourceField", mapping.sourceField());
    item.put("targetField", mapping.targetField());
    item.put("enabled", mapping.enabled());
    return item;
  }

  private CsvImportResult parseCsv(MultipartFile file) {
    int total = 0;
    int created = 0;
    int updated = 0;
    int skipped = 0;
    int unmatchedCount = 0;
    List<Integer> unmatchedRows = new ArrayList<>();
    List<CsvImportResult.RowError> errors = new ArrayList<>();
    Set<String> seenPhones = new LinkedHashSet<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        return new CsvImportResult(0, 0, 0, 0, List.of(new CsvImportResult.RowError(1, "empty csv")));
      }
      List<String> headers = parseLine(headerLine);
      int phoneIndex = headers.indexOf("phone");
      if (phoneIndex < 0) {
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, "csv must contain phone column");
      }
      String line;
      while ((line = reader.readLine()) != null) {
        total++;
        if (total > IMPORT_MAX_ROWS) {
          throw new ApiException(ApiErrorCodes.BAD_REQUEST, "single import max rows is 5000");
        }
        List<String> values = parseLine(line);
        String phone = phoneIndex < values.size() ? values.get(phoneIndex).trim() : "";
        if (!phone.matches("\\d{11}")) {
          skipped++;
          errors.add(new CsvImportResult.RowError(total + 1, "phone invalid"));
          continue;
        }
        if (!seenPhones.add(phone)) {
          skipped++;
          errors.add(new CsvImportResult.RowError(total + 1, "duplicate phone in same file"));
          continue;
        }
        Customer customer = customerRepository.findByPhone(phone).orElseGet(Customer::new);
        boolean exists = customer.getPhone() != null;
        customer.setPhone(phone);
        customer.setSourceTable(customer.getSourceTable() == null ? "CSV_IMPORT" : customer.getSourceTable());
        customer.setSyncedAt(LocalDateTime.now());
        Map<String, Object> rawFields = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
          String header = headers.get(index);
          if (header.equals("phone") || index >= values.size() || values.get(index).isBlank()) {
            continue;
          }
          rawFields.put(header, values.get(index));
        }
        TagExchangeResult exchange = exchangeService == null
            ? new TagExchangeResult(rawFields, List.of(), List.of())
            : exchangeService.prepareInbound(
                TagExchangeSourceType.CSV_IMPORT,
                String.valueOf(total + 1),
                rawFields);
        applyCsvFields(customer, exchange.acceptedFields());
        if (!exchange.unmatched().isEmpty()) {
          unmatchedCount += exchange.unmatched().size();
          unmatchedRows.add(total + 1);
        }
        customerRepository.upsert(
            customer,
            exchange,
            TagExchangeSourceType.CSV_IMPORT,
            String.valueOf(total + 1));
        if (exists) {
          updated++;
        } else {
          created++;
        }
      }
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "csv import failed");
    }
    return new CsvImportResult(total, created, updated, skipped, errors, unmatchedCount, unmatchedRows);
  }

  private void applyCsvFields(Customer customer, Map<String, Object> fields) {
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      if ("phone".equals(entry.getKey())) {
        continue;
      }
      if ("nickname".equals(entry.getKey()) && !isBlank(customer.getNickname())) {
        continue;
      }
      setCustomerField(customer, entry.getKey(), entry.getValue());
    }
  }

  private void setCustomerField(Customer customer, String field, Object raw) {
    try {
      PropertyDescriptor descriptor = new PropertyDescriptor(field, Customer.class);
      Method setter = descriptor.getWriteMethod();
      if (setter == null) {
        return;
      }
      setter.invoke(customer, convertCustomerField(descriptor.getPropertyType(), raw));
    } catch (Exception ex) {
      // Unknown CSV columns remain ignored, matching the existing import behavior.
    }
  }

  private Object convertCustomerField(Class<?> type, Object raw) {
    String value = String.valueOf(raw).trim();
    if (String.class.equals(type)) {
      return value;
    }
    if (BigDecimal.class.equals(type)) {
      return new BigDecimal(value);
    }
    if (LocalDate.class.equals(type)) {
      return LocalDate.parse(value);
    }
    if (LocalDateTime.class.equals(type)) {
      return parseDateTime(value);
    }
    return value;
  }

  private LocalDateTime parseDateTime(String value) {
    if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
      return LocalDate.parse(value).atStartOfDay();
    }
    return LocalDateTime.parse(value.replace(' ', 'T'));
  }

  private List<String> parseLine(String line) {
    return List.of(line.split(",", -1)).stream().map(String::trim).toList();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private void validateDatasource(DatasourceRequest request, boolean create, Long existingId) {
    if (request == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "request body required");
    }
    if (create && (request.name() == null || request.name().isBlank())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "name is required");
    }
    if (request.name() != null && request.name().length() > 100) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "name max length is 100");
    }
    if (request.name() != null && !request.name().isBlank() && repository.nameExists(request.name().trim(), existingId)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource name already exists");
    }
    if (create && (request.sheetId() == null || request.sheetId().isBlank())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "sheetId is required");
    }
    if (create && (request.sourceTable() == null || request.sourceTable().isBlank())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "sourceTable is required");
    }
  }

  private List<FieldMappingDto> validateMappings(MappingSaveRequest request) {
    if (request == null || request.mappings() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "mappings required");
    }
    Set<String> targets = new LinkedHashSet<>();
    for (FieldMappingDto mapping : request.mappings()) {
      if (mapping.sourceField() == null || mapping.sourceField().isBlank() || mapping.targetField() == null || mapping.targetField().isBlank()) {
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, "sourceField and targetField required");
      }
      if (mapping.enabled() && !targets.add(mapping.targetField())) {
        throw new ApiException(ApiErrorCodes.CONFLICT, "same targetField can only have one enabled mapping");
      }
    }
    return request.mappings();
  }

  private String toJson(List<FieldMappingDto> mappings) {
    try {
      return objectMapper.writeValueAsString(mappings);
    } catch (Exception ex) {
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "mapping snapshot failed");
    }
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      return "{}";
    }
  }

  private List<FieldMappingDto> fromJson(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<List<FieldMappingDto>>() {});
    } catch (Exception ex) {
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "mapping snapshot parse failed");
    }
  }

  private void publish(String key) {
    eventPublisher.publishEvent(new ConfigChangedEvent(key));
    wsPushService.broadcastWs(WsMessage.unsaved("CONFIG_REFRESH", Map.of("configKey", key)));
  }

  private Map<String, Object> datasourceDetail(Datasource datasource) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("id", datasource.id());
    detail.put("name", datasource.name());
    detail.put("sheetId", datasource.sheetId());
    detail.put("sourceTable", datasource.sourceTable());
    detail.put("enabled", datasource.enabled());
    return detail;
  }

  private void audit(String action, Datasource datasource, Map<String, Object> detail) {
    auditLogger.log(action, AuthContext.username(), "datasource", String.valueOf(datasource.id()), toJson(detail));
  }

  private String label(String field) {
    String approvedLabel = CustomerMasterFieldCatalog.labelOf(field);
    if (!approvedLabel.equals(field)) {
      return approvedLabel;
    }
    return switch (field) {
      case "phone" -> "手机号";
      case "nickname" -> "客户昵称";
      case "wechatId" -> "微信号";
      case "sourceChannel" -> "来源渠道";
      case "leadType" -> "客资类型";
      case "leadCaptureType" -> "留资类型";
      case "leadCaptureMethod" -> "留资方式";
      case "platformLeadAt" -> "平台留资时间";
      case "personalityType" -> "性格类型";
      case "assignedKeeper" -> "分配管家";
      case "assignedAt" -> "分配日期";
      case "intendedStore" -> "意向门店";
      case "intendedProject" -> "意向项目";
      case "purchasedProject" -> "已购项目";
      case "postpartumMonths" -> "产后月份";
      case "parity" -> "胎次";
      case "deliveryMethod" -> "分娩方式";
      case "breastfeeding" -> "哺乳情况";
      case "lochiaPeriod" -> "恶露/月经情况";
      case "pregnancyWeight" -> "孕期增重";
      case "currentWeight" -> "当前体重";
      case "bodyConcerns" -> "身体关注点";
      case "diastasisRecti" -> "腹直肌分离";
      case "urineLeakage" -> "漏尿情况";
      case "pubicLumbago" -> "耻骨/腰痛";
      case "prevRepairExp" -> "既往修复经历";
      case "postpartumCheck" -> "产后检查";
      case "exerciseHabits" -> "运动习惯";
      case "intentLevel" -> "意向等级";
      case "worries" -> "客户顾虑";
      case "customerStage" -> "客户阶段";
      case "internalNote" -> "备注";
      case "customerProfileSummary" -> "客户B档案";
      case "firstTrackingCapture" -> "第一次追踪捕捉";
      case "secondTrackingCapture" -> "第二次追踪捕捉";
      case "thirdTrackingCapture" -> "第三次追踪捕捉";
      case "lastFollowupAt" -> "最近跟进时间";
      case "followupNotes" -> "跟进记录";
      case "nextFollowupAt" -> "下次跟进时间";
      case "nextFollowupDir" -> "下次跟进方向";
      case "appointmentDate" -> "预约日期";
      case "appointmentStore" -> "预约门店";
      case "appointmentItem" -> "预约项目";
      case "arrived" -> "是否到店";
      case "appointmentStatus" -> "预约状态";
      case "appointmentTime" -> "预约时间";
      case "arrivalSourceRowId" -> "到店衔接记录";
      case "sourceTable" -> "数据来源表";
      default -> field;
    };
  }

  private String category(String field) {
    if (field.equals("wechatId") || field.equals("leadCaptureType") || field.equals("leadCaptureMethod")
        || field.equals("platformLeadAt") || field.equals("assignedAt")) {
      return "留资与分配";
    }
    return field.contains("Weight") || field.contains("postpartum") || field.contains("delivery") ? "身体数据" : "基本信息";
  }

  private void requireAdmin() {
    AuthUser user = AuthContext.current();
    if (user == null || user.role() != Role.ADMIN) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "管理员权限不足");
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
