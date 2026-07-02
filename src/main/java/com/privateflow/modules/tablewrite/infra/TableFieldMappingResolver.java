package com.privateflow.modules.tablewrite.infra;

import com.privateflow.common.events.ConfigChangedEvent;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TableFieldMappingResolver {

  private final JdbcTemplate jdbcTemplate;
  private volatile Map<String, Map<String, String>> mappings = Map.of();

  public TableFieldMappingResolver(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    reload();
  }

  public Map<String, Object> toSourceFields(String sourceTable, Map<String, Object> internalFields) {
    Map<String, String> reverse = mappings.getOrDefault(sourceTable, Map.of());
    Map<String, Object> mapped = new HashMap<>();
    internalFields.forEach((field, value) -> {
      if (value != null) {
        mapped.put(reverse.getOrDefault(field, field), value);
      }
    });
    return mapped;
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
