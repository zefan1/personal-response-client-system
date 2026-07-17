package com.privateflow.modules.customer.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.sync.CustomerSyncScheduler;
import com.privateflow.modules.customer.sync.SheetClient;
import com.privateflow.modules.customer.sync.SheetRow;
import com.privateflow.modules.customer.sync.SheetSource;
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
import java.util.ArrayList;
import java.util.Comparator;
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
      TagExchangeService exchangeService) {
    this.repository = repository;
    this.customerRepository = customerRepository;
    this.syncScheduler = syncScheduler;
    this.sheetClient = sheetClient;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
    this.objectMapper = objectMapper;
    this.auditLogger = auditLogger;
    this.exchangeService = exchangeService;
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
        null);
  }

  public Map<String, Object> list() {
    List<Datasource> datasources = repository.list();
    return Map.of("datasources", datasources, "total", datasources.size());
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
    repository.replaceMappings(datasource.sourceTable(), mappings);
    int version = repository.createMappingVersion(id, toJson(mappings), mappings.size(), "replace mappings: " + mappings.size(), AuthContext.username());
    publish(FIELD_MAPPING_CONFIG_KEY);
    audit("DATASOURCE_MAPPING_SAVE", datasource, Map.of(
        "datasourceId", datasource.id(),
        "sourceTable", datasource.sourceTable(),
        "mappingCount", mappings.size(),
        "version", version));
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
    String source = "MAPPING_CONFIG";
    String fetchStatus = "NOT_ATTEMPTED";
    String fetchError = null;
    try {
      List<SheetRow> rows = sheetClient.fetchIncrementalRows(sheetSource(datasource), LocalDateTime.of(1970, 1, 1, 0, 0), 20);
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
    if (!syncScheduler.tryStartOneAsync(sheetSource(datasource))) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "datasource sync already running");
    }
    audit("DATASOURCE_SYNC_START", datasource, Map.of("datasourceId", id, "sourceTable", datasource.sourceTable()));
    return Map.of("accepted", true, "datasourceId", id, "sourceTable", datasource.sourceTable());
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
      return LocalDateTime.parse(value);
    }
    return value;
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
    return switch (field) {
      case "phone" -> "手机号";
      case "nickname" -> "客户昵称";
      case "sourceChannel" -> "来源渠道";
      case "leadType" -> "线索类型";
      case "personalityType" -> "性格类型";
      case "assignedKeeper" -> "分配管家";
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
      case "lastFollowupAt" -> "最近跟进时间";
      case "followupNotes" -> "跟进记录";
      case "nextFollowupAt" -> "下次跟进时间";
      case "nextFollowupDir" -> "下次跟进方向";
      case "appointmentDate" -> "预约日期";
      case "appointmentStore" -> "预约门店";
      case "appointmentItem" -> "预约项目";
      case "arrived" -> "是否到店";
      case "sourceTable" -> "数据来源表";
      default -> field;
    };
  }

  private String category(String field) {
    return field.contains("Weight") || field.contains("postpartum") || field.contains("delivery") ? "身体数据" : "基本信息";
  }
}
