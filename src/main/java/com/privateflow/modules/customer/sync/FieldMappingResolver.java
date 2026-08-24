package com.privateflow.modules.customer.sync;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.admin.CustomerStageOptionService;
import com.privateflow.modules.customer.LeadTypes;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FieldMappingResolver {

  private static final Logger log = LoggerFactory.getLogger(FieldMappingResolver.class);
  private final JdbcTemplate jdbcTemplate;
  private final TagExchangeService exchangeService;
  private final CustomerStageOptionService stageOptionService;
  private volatile Map<String, Map<String, String>> mappings = Map.of();

  @Autowired
  public FieldMappingResolver(JdbcTemplate jdbcTemplate, TagExchangeService exchangeService,
      CustomerStageOptionService stageOptionService) {
    this.jdbcTemplate = jdbcTemplate;
    this.exchangeService = exchangeService;
    this.stageOptionService = stageOptionService;
    reload();
  }

  public FieldMappingResolver(JdbcTemplate jdbcTemplate, TagExchangeService exchangeService) {
    this(jdbcTemplate, exchangeService, null);
  }

  public FieldMappingResolver(JdbcTemplate jdbcTemplate) {
    this(jdbcTemplate, null);
  }

  public Customer mapRow(String sourceTable, SheetRow row) {
    return mapRowResult(sourceTable, row).customer();
  }

  /** Returns target-field -> configured external source-field mappings for provenance. */
  public Map<String, String> sourceFieldsFor(String sourceTable) {
    Map<String, String> tableMappings = mappings.getOrDefault(sourceTable, Map.of());
    Map<String, String> result = new HashMap<>();
    tableMappings.forEach((sourceField, targetField) -> result.put(targetField, sourceField));
    return Map.copyOf(result);
  }

  public FieldMappingResult mapRowResult(String sourceTable, SheetRow row) {
    Customer customer = new Customer();
    customer.setSourceTable(sourceTable);
    customer.setSourceRowId(row.rowId());
    customer.setSyncedAt(LocalDateTime.now());
    Map<String, String> tableMappings = mappings.getOrDefault(sourceTable, Map.of());
    if (tableMappings.isEmpty()) {
      throw new IllegalStateException("no enabled field mappings configured for source table: " + sourceTable);
    }
    Map<String, Object> mappedFields = new HashMap<>();
    for (Map.Entry<String, String> entry : tableMappings.entrySet()) {
      String raw = row.values().get(entry.getKey());
      if (raw == null || raw.isBlank()) {
        continue;
      }
      mappedFields.put(entry.getValue(), raw);
    }
    applyDerivedFields(sourceTable, mappedFields);
    TagExchangeResult exchange = exchangeService == null
        ? new TagExchangeResult(mappedFields, List.of(), List.of())
        : exchangeService.prepareInbound(
            TagExchangeSourceType.EXTERNAL_SYNC,
            row.rowId(),
            mappedFields);
    for (Map.Entry<String, Object> entry : exchange.acceptedFields().entrySet()) {
      String value = String.valueOf(entry.getValue());
      if ("customerStage".equals(entry.getKey()) && stageOptionService != null) {
        value = stageOptionService.normalize(sourceTable, value);
      }
      set(customer, entry.getKey(), value);
    }
    customer.setLeadType(LeadTypes.normalize(customer.getLeadType()));
    return new FieldMappingResult(customer, exchange, mappedFields.keySet());
  }

  /**
   * Returns the configured inbound fields actually returned for one Smart Sheet row. Empty cells
   * are preserved as empty strings so callback processing can deliberately clear an allowed
   * customer field, while an omitted API value never clears a database field by accident.
   */
  public Map<String, String> mappedRawValues(String sourceTable, SheetRow row) {
    Map<String, String> tableMappings = mappings.getOrDefault(sourceTable, Map.of());
    if (tableMappings.isEmpty()) {
      throw new IllegalStateException("no enabled field mappings configured for source table: " + sourceTable);
    }
    Map<String, String> result = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : tableMappings.entrySet()) {
      if (!row.values().containsKey(entry.getKey())) {
        continue;
      }
      String value = row.values().get(entry.getKey());
      result.put(entry.getValue(), value == null ? "" : value.trim());
    }
    return Map.copyOf(result);
  }

  /** Applies only the named, configured fields and reports fields whose values changed. */
  public Set<String> applyMappedValues(Customer customer, Map<String, String> values, Set<String> excludedFields) {
    Set<String> changed = new LinkedHashSet<>();
    if (customer == null || values == null) {
      return changed;
    }
    for (Map.Entry<String, String> entry : values.entrySet()) {
      String field = entry.getKey();
      if (field == null || field.isBlank() || (excludedFields != null && excludedFields.contains(field))) {
        continue;
      }
      try {
        PropertyDescriptor descriptor = new PropertyDescriptor(field, Customer.class);
        Method getter = descriptor.getReadMethod();
        Method setter = descriptor.getWriteMethod();
        if (getter == null || setter == null) {
          continue;
        }
        Object before = getter.invoke(customer);
        Object after = entry.getValue() == null || entry.getValue().isBlank()
            ? null : convert(descriptor.getPropertyType(), entry.getValue());
        if (!java.util.Objects.equals(before, after)) {
          setter.invoke(customer, after);
          changed.add(field);
        }
      } catch (Exception ex) {
        log.warn("skip invalid customer field mapping field={}", field);
      }
    }
    if (changed.contains("leadType")) {
      customer.setLeadType(LeadTypes.normalize(customer.getLeadType()));
    }
    return Set.copyOf(changed);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onConfigChanged(ConfigChangedEvent event) {
    if ("datasource.field_mappings".equals(event.configKey())) {
      reload();
    }
  }

  public final void reload() {
    try {
      List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
          SELECT source_table, source_field, target_field
          FROM datasource_field_mappings
          WHERE is_enabled = 1
          ORDER BY source_table, id
          """);
      Map<String, Map<String, String>> loaded = new HashMap<>();
      for (Map<String, Object> row : rows) {
        loaded.computeIfAbsent(row.get("source_table").toString(), ignored -> new HashMap<>())
            .put(row.get("source_field").toString(), row.get("target_field").toString());
      }
      mappings = loaded;
    } catch (RuntimeException ex) {
      mappings = Map.of();
      log.warn("field mappings reload failed, cleared mapping snapshot: {}", ex.getMessage());
    }
  }

  private void set(Customer customer, String field, String raw) {
    try {
      PropertyDescriptor descriptor = new PropertyDescriptor(field, Customer.class);
      Method setter = descriptor.getWriteMethod();
      Class<?> type = descriptor.getPropertyType();
      Object value = convert(type, raw);
      setter.invoke(customer, value);
    } catch (Exception ex) {
      log.warn("skip invalid customer field mapping field={}, raw={}", field, raw);
    }
  }

  private Object convert(Class<?> type, String raw) {
    String value = raw.trim();
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

  /** Applies the confirmed business rules that cannot be represented by a direct field mapping. */
  private void applyDerivedFields(String sourceTable, Map<String, Object> mappedFields) {
    if (sourceTable == null || !sourceTable.startsWith("ASSIGNMENT:")) {
      return;
    }
    Object purchasedProject = mappedFields.get("purchasedProject");
    if (purchasedProject != null && !purchasedProject.toString().isBlank()) {
      mappedFields.put("experienceCardType", recognizeExperienceCardType(purchasedProject.toString()));
    }
    Object assignedAt = mappedFields.get("assignedAt");
    if (assignedAt != null && !assignedAt.toString().isBlank()) {
      parseAssignmentDate(assignedAt.toString()).ifPresent(date ->
          mappedFields.put("assignmentMonth", "%02d年%d月".formatted(date.getYear() % 100, date.getMonthValue())));
    }
  }

  private String recognizeExperienceCardType(String purchasedProject) {
    if (purchasedProject.contains("孕") && purchasedProject.contains("按")) {
      return "孕按";
    }
    if (purchasedProject.contains("通") || purchasedProject.contains("乳") || purchasedProject.contains("母乳")) {
      return "通乳";
    }
    return "产康";
  }

  private java.util.Optional<LocalDate> parseAssignmentDate(String raw) {
    String value = raw.trim();
    for (DateTimeFormatter formatter : List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy/M/d"),
        DateTimeFormatter.ofPattern("yyyy年M月d日"))) {
      try {
        return java.util.Optional.of(LocalDate.parse(value, formatter));
      } catch (DateTimeParseException ignored) {
        // Try the next format used by Smart Sheet exports.
      }
    }
    try {
      return java.util.Optional.of(parseDateTime(value).toLocalDate());
    } catch (RuntimeException ignored) {
      return java.util.Optional.empty();
    }
  }

}
