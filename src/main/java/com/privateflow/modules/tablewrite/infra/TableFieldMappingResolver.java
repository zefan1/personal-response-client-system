package com.privateflow.modules.tablewrite.infra;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.tablewrite.TableWriteErrorCodes;
import com.privateflow.modules.tablewrite.TableWriteException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TableFieldMappingResolver {

  private static final Logger log = LoggerFactory.getLogger(TableFieldMappingResolver.class);
  private final JdbcTemplate jdbcTemplate;
  private volatile Map<String, Map<String, String>> mappings = Map.of();

  public TableFieldMappingResolver(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    reload();
  }

  public Map<String, Object> toSourceFields(String sourceTable, Map<String, Object> internalFields) {
    Map<String, String> reverse = mappings.getOrDefault(sourceTable, Map.of());
    if (reverse.isEmpty()) {
      throw new TableWriteException(
          TableWriteErrorCodes.CONFIG_MISSING,
          "no enabled field mappings configured for source table: " + sourceTable);
    }
    Map<String, Object> mapped = new HashMap<>();
    internalFields.forEach((field, value) -> {
      if (value != null) {
        String sourceField = reverse.get(field);
        if (sourceField == null || sourceField.isBlank()) {
          log.warn("skip table write field without mapping, sourceTable={}, field={}", sourceTable, field);
          return;
        }
        mapped.put(sourceField, value);
      }
    });
    return mapped;
  }

  public Map<String, Object> toInternalFields(String sourceTable, Map<String, Object> sourceFields) {
    Map<String, String> reverse = mappings.getOrDefault(sourceTable, Map.of());
    if (reverse.isEmpty()) {
      throw new TableWriteException(
          TableWriteErrorCodes.CONFIG_MISSING,
          "no enabled field mappings configured for source table: " + sourceTable);
    }
    Map<String, Object> mapped = new LinkedHashMap<>();
    sourceFields.forEach((sourceField, value) -> {
      String targetField = reverse.entrySet().stream()
          .filter(entry -> entry.getValue().equals(sourceField))
          .map(Map.Entry::getKey)
          .findFirst()
          .orElse(null);
      if (targetField != null && value != null) {
        mapped.put(targetField, value);
      }
    });
    return mapped;
  }

  public Map<String, Object> mergeSourceFields(
      String sourceTable,
      Map<String, Object> originalSourceFields,
      Map<String, Object> acceptedInternalFields) {
    return mergeSourceFields(sourceTable, originalSourceFields, acceptedInternalFields, List.of());
  }

  public Map<String, Object> mergeSourceFields(
      String sourceTable,
      Map<String, Object> originalSourceFields,
      Map<String, Object> acceptedInternalFields,
      List<String> filteredInternalFields) {
    Map<String, String> reverse = mappings.getOrDefault(sourceTable, Map.of());
    if (reverse.isEmpty()) {
      throw new TableWriteException(
          TableWriteErrorCodes.CONFIG_MISSING,
          "no enabled field mappings configured for source table: " + sourceTable);
    }
    Map<String, Object> merged = new LinkedHashMap<>(originalSourceFields);
    for (String filteredField : filteredInternalFields) {
      String sourceField = reverse.get(filteredField);
      if (sourceField != null) {
        merged.remove(sourceField);
      }
    }
    acceptedInternalFields.forEach((targetField, value) -> {
      String sourceField = reverse.get(targetField);
      if (sourceField != null && value != null) {
        merged.put(sourceField, value);
      }
    });
    return merged;
  }

  @EventListener
  public void onConfigChanged(ConfigChangedEvent event) {
    if ("datasource.field_mappings".equals(event.configKey())) {
      reload();
    }
  }

  public final void reload() {
    try {
      Map<String, Map<String, String>> loaded = new HashMap<>();
      jdbcTemplate.queryForList("""
          SELECT source_table, source_field, target_field
          FROM datasource_field_mappings
          WHERE is_enabled = 1
          ORDER BY source_table, id
          """).forEach(row -> loaded
          .computeIfAbsent(row.get("source_table").toString(), ignored -> new HashMap<>())
          .put(row.get("target_field").toString(), row.get("source_field").toString()));
      mappings = loaded;
    } catch (RuntimeException ex) {
      mappings = Map.of();
    }
  }
}
