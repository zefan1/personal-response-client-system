package com.privateflow.modules.customer.sync;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.LeadTypes;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FieldMappingResolver {

  private static final Logger log = LoggerFactory.getLogger(FieldMappingResolver.class);
  private final JdbcTemplate jdbcTemplate;
  private final TagExchangeService exchangeService;
  private volatile Map<String, Map<String, String>> mappings = Map.of();

  @Autowired
  public FieldMappingResolver(JdbcTemplate jdbcTemplate, TagExchangeService exchangeService) {
    this.jdbcTemplate = jdbcTemplate;
    this.exchangeService = exchangeService;
    reload();
  }

  public FieldMappingResolver(JdbcTemplate jdbcTemplate) {
    this(jdbcTemplate, null);
  }

  public Customer mapRow(String sourceTable, SheetRow row) {
    return mapRowResult(sourceTable, row).customer();
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
    TagExchangeResult exchange = exchangeService == null
        ? new TagExchangeResult(mappedFields, List.of(), List.of())
        : exchangeService.prepareInbound(
            TagExchangeSourceType.EXTERNAL_SYNC,
            row.rowId(),
            mappedFields);
    for (Map.Entry<String, Object> entry : exchange.acceptedFields().entrySet()) {
      set(customer, entry.getKey(), String.valueOf(entry.getValue()));
    }
    customer.setLeadType(LeadTypes.normalize(customer.getLeadType()));
    return new FieldMappingResult(customer, exchange);
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
