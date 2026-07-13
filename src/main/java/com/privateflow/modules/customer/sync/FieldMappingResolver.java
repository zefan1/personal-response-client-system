package com.privateflow.modules.customer.sync;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.LeadTypes;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FieldMappingResolver {

  private static final Logger log = LoggerFactory.getLogger(FieldMappingResolver.class);
  private final JdbcTemplate jdbcTemplate;
  private volatile Map<String, Map<String, String>> mappings = Map.of();

  public FieldMappingResolver(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    reload();
  }

  public Customer mapRow(String sourceTable, SheetRow row) {
    Customer customer = new Customer();
    customer.setSourceTable(sourceTable);
    customer.setSourceRowId(row.rowId());
    customer.setSyncedAt(LocalDateTime.now());
    Map<String, String> tableMappings = mappings.getOrDefault(sourceTable, Map.of());
    if (tableMappings.isEmpty()) {
      throw new IllegalStateException("no enabled field mappings configured for source table: " + sourceTable);
    }
    for (Map.Entry<String, String> entry : tableMappings.entrySet()) {
      String raw = row.values().get(entry.getKey());
      if (raw == null || raw.isBlank()) {
        continue;
      }
      set(customer, entry.getValue(), raw);
    }
    customer.setLeadType(LeadTypes.normalize(customer.getLeadType()));
    return customer;
  }

  @EventListener
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
      log.warn("field mappings reload failed, keeping previous mapping snapshot: {}", ex.getMessage());
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
      return LocalDateTime.parse(value);
    }
    return value;
  }

}
