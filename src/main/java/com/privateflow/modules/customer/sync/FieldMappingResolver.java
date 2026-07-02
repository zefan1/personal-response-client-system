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
  private volatile Map<String, Map<String, String>> mappings = defaultMappings();

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
    for (Map.Entry<String, String> entry : tableMappings.entrySet()) {
      String raw = row.values().get(entry.getKey());
      if (raw == null || raw.isBlank()) {
        continue;
      }
      set(customer, entry.getValue(), raw);
    }
    if ("推广组客资登记表".equals(sourceTable) && customer.getLeadType() == null) {
      customer.setLeadType(inferPromoLeadType(row));
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
      if (rows.isEmpty()) {
        mappings = defaultMappings();
        return;
      }
      Map<String, Map<String, String>> loaded = new HashMap<>();
      for (Map<String, Object> row : rows) {
        loaded.computeIfAbsent(row.get("source_table").toString(), ignored -> new HashMap<>())
            .put(row.get("source_field").toString(), row.get("target_field").toString());
      }
      mappings = loaded;
    } catch (RuntimeException ex) {
      log.warn("field mappings reload failed, keeping previous/default mappings: {}", ex.getMessage());
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

  private String inferPromoLeadType(SheetRow row) {
    String project = row.values().getOrDefault("下单项目", "");
    if (project.contains("团购") || project.contains("体验券")) {
      return LeadTypes.TUAN_GOU;
    }
    return LeadTypes.XIAN_SUO;
  }

  private static Map<String, Map<String, String>> defaultMappings() {
    return Map.of(
        "推广组客资登记表", Map.of(
            "手机号/微信", "phone",
            "意向门店", "intendedStore",
            "对接管家", "assignedKeeper",
            "下单项目", "intendedProject",
            "来源渠道", "sourceChannel"),
        "私域客资管理表", Map.ofEntries(
            Map.entry("联系方式", "phone"),
            Map.entry("备注称呼", "nickname"),
            Map.entry("客资渠道", "sourceChannel"),
            Map.entry("客资类型", "leadType"),
            Map.entry("管家", "assignedKeeper"),
            Map.entry("意向门店", "intendedStore"),
            Map.entry("意向项目", "intendedProject"),
            Map.entry("客户阶段", "customerStage"),
            Map.entry("客户关注点", "bodyConcerns"),
            Map.entry("跟进记录", "followupNotes"),
            Map.entry("下次跟进时间", "nextFollowupAt"),
            Map.entry("下次跟进方向", "nextFollowupDir")),
        "新客管理衔接表", Map.of(
            "手机号码", "phone",
            "客户姓名", "nickname",
            "所属门店", "appointmentStore",
            "到店日期", "appointmentDate",
            "是否到店", "arrived",
            "体验项目", "appointmentItem",
            "约课管家", "assignedKeeper"));
  }
}
