package com.privateflow.modules.customer.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.sync.CustomerSyncScheduler;
import com.privateflow.modules.customer.sync.SheetClient;
import com.privateflow.modules.customer.sync.SheetRow;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.springframework.context.ApplicationEventPublisher;
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

  public DatasourceAdminService(
      DatasourceAdminRepository repository,
      CustomerRepository customerRepository,
      CustomerSyncScheduler syncScheduler,
      SheetClient sheetClient,
      ApplicationEventPublisher eventPublisher,
      WsPushService wsPushService,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.customerRepository = customerRepository;
    this.syncScheduler = syncScheduler;
    this.sheetClient = sheetClient;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> list() {
    List<Datasource> datasources = repository.list();
    return Map.of("datasources", datasources, "total", datasources.size());
  }

  public Datasource create(DatasourceRequest request) {
    validateDatasource(request, true, null);
    long id = repository.create(request, AuthContext.username());
    publish(CONNECTIONS_CONFIG_KEY);
    return repository.find(id).orElseThrow();
  }

  public Datasource update(long id, DatasourceRequest request) {
    repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    validateDatasource(request, false, id);
    repository.update(id, request);
    publish(CONNECTIONS_CONFIG_KEY);
    return repository.find(id).orElseThrow();
  }

  @Transactional
  public Map<String, Object> delete(long id) {
    Datasource datasource = repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    int deletedMappings = repository.deleteMappings(datasource.sourceTable());
    repository.delete(id);
    publish(CONNECTIONS_CONFIG_KEY);
    publish(FIELD_MAPPING_CONFIG_KEY);
    return Map.of("deletedMappings", deletedMappings);
  }

  public Datasource toggle(long id, boolean enabled) {
    repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    repository.toggle(id, enabled);
    publish(CONNECTIONS_CONFIG_KEY);
    return repository.find(id).orElseThrow();
  }

  public Map<String, Object> replace(long id, DatasourceReplaceRequest request) {
    if (request.sheetId() == null || request.sheetId().isBlank()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "sheetId is required");
    }
    repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    String oldSheetId = repository.replace(id, request.sheetId().trim());
    publish(CONNECTIONS_CONFIG_KEY);
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
    repository.replaceMappings(datasource.sourceTable(), mappings);
    int version = repository.createMappingVersion(id, toJson(mappings), mappings.size(), "replace mappings: " + mappings.size(), AuthContext.username());
    publish(FIELD_MAPPING_CONFIG_KEY);
    return Map.of("mappingCount", mappings.size(), "version", version);
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
    repository.replaceMappings(datasource.sourceTable(), mappings);
    int newVersion = repository.createMappingVersion(id, toJson(mappings), mappings.size(), "restore from version " + request.version(), AuthContext.username());
    publish(FIELD_MAPPING_CONFIG_KEY);
    return Map.of("restoredVersion", request.version(), "newVersion", newVersion, "mappingCount", mappings.size());
  }

  public Map<String, Object> columns(long id) {
    Datasource datasource = repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    LinkedHashSet<String> columnNames = new LinkedHashSet<>();
    String source = "MAPPING_CONFIG";
    String fetchStatus = "NOT_ATTEMPTED";
    String fetchError = null;
    try {
      List<SheetRow> rows = sheetClient.fetchIncrementalRows(datasource.sourceTable(), LocalDateTime.of(1970, 1, 1, 0, 0), 20);
      for (SheetRow row : rows) {
        columnNames.addAll(row.values().keySet());
      }
      fetchStatus = "OK";
      if (!columnNames.isEmpty()) {
        source = "SHEET_SAMPLE";
      }
    } catch (RuntimeException ex) {
      fetchStatus = "UNAVAILABLE";
      fetchError = ex.getMessage();
    }
    List<FieldMappingDto> mappings = repository.mappings(datasource.sourceTable());
    for (FieldMappingDto mapping : mappings) {
      columnNames.add(mapping.sourceField());
    }
    List<Map<String, Object>> columns = columnNames.stream()
        .sorted(Comparator.naturalOrder())
        .map(column -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("name", column);
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
    result.put("fallback", !"SHEET_SAMPLE".equals(source));
    if (fetchError != null && !fetchError.isBlank()) {
      result.put("fetchError", fetchError);
    }
    return result;
  }

  public Map<String, Object> customerFields() {
    List<CustomerFieldDto> fields = new ArrayList<>();
    try {
      for (PropertyDescriptor descriptor : Introspector.getBeanInfo(Customer.class).getPropertyDescriptors()) {
        if (descriptor.getWriteMethod() == null || SYSTEM_FIELDS.contains(descriptor.getName())) {
          continue;
        }
        fields.add(new CustomerFieldDto(descriptor.getName(), label(descriptor.getName()), category(descriptor.getName())));
      }
    } catch (Exception ex) {
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "customer fields introspection failed");
    }
    return Map.of("fields", fields);
  }

  public Map<String, Object> syncStatus() {
    List<Map<String, Object>> items = repository.list().stream()
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
    Datasource datasource = repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
    if (!datasource.enabled()) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "datasource is disabled");
    }
    CompletableFuture.runAsync(syncScheduler::runOnce);
    return Map.of("accepted", true, "datasourceId", id, "sourceTable", datasource.sourceTable());
  }

  public CsvImportResult importCsv(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "csv file is required");
    }
    CsvImportResult result = parseCsv(file);
    repository.logImport(file.getOriginalFilename(), result, AuthContext.username());
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
        if (headers.contains("nickname")) {
          int index = headers.indexOf("nickname");
          if (index < values.size() && isBlank(customer.getNickname())) {
            customer.setNickname(values.get(index));
          }
        }
        customerRepository.upsert(customer);
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
    return new CsvImportResult(total, created, updated, skipped, errors);
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

  private String label(String field) {
    return switch (field) {
      case "phone" -> "手机号";
      case "nickname" -> "客户昵称";
      case "leadType" -> "线索类型";
      case "assignedKeeper" -> "分配管家";
      case "intendedStore" -> "意向门店";
      case "intendedProject" -> "意向项目";
      case "customerStage" -> "客户阶段";
      default -> field;
    };
  }

  private String category(String field) {
    return field.contains("Weight") || field.contains("postpartum") || field.contains("delivery") ? "身体数据" : "基本信息";
  }
}
