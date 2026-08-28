package com.privateflow.modules.arrival;

import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.history.CustomerFieldHistoryContext;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.profile.infra.ProfileFieldRegistry;
import com.privateflow.modules.profile.infra.ProfileWriter;
import com.privateflow.modules.quicksearch.QuickSearchRepository;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetField;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetFieldCatalog;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manual appointment entry point. It matches a customer but never recognizes appointment content. */
@Service
public class ManualAppointmentService {
  private static final DateTimeFormatter FORM_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
  private static final Set<String> HIDDEN_FORM_FIELDS = Set.of("arrived", "transactionAmount", "transactionAt", "transactionPrimaryReason");
  private final CustomerQueryService customers;
  private final CustomerAccessService access;
  private final ProfileWriter writer;
  private final ProfileFieldRegistry profileFields;
  private final ArrivalHandoverTaskRepository tasks;
  private final ArrivalHandoverService handover;
  private final ArrivalReportStorage reports;
  private final AuxiliarySmartSheetTargets targets;
  private final WecomSmartSheetFieldCatalog fieldCatalog;
  private final TableConfigProvider tableConfig;
  private final JdbcTemplate jdbc;
  private final SystemConfigRepository configs;
  private final QuickSearchRepository quickSearch;

  public ManualAppointmentService(CustomerQueryService customers, CustomerAccessService access, ProfileWriter writer,
      ProfileFieldRegistry profileFields, ArrivalHandoverTaskRepository tasks, ArrivalHandoverService handover,
      ArrivalReportStorage reports, AuxiliarySmartSheetTargets targets, WecomSmartSheetFieldCatalog fieldCatalog,
      TableConfigProvider tableConfig, JdbcTemplate jdbc, SystemConfigRepository configs,
      QuickSearchRepository quickSearch) {
    this.customers = customers; this.access = access; this.writer = writer; this.profileFields = profileFields;
    this.tasks = tasks; this.handover = handover; this.reports = reports; this.targets = targets;
    this.fieldCatalog = fieldCatalog; this.tableConfig = tableConfig; this.jdbc = jdbc;
    this.configs = configs; this.quickSearch = quickSearch;
  }

  public ManualAppointmentForm form(long customerId) {
    Customer customer = require(customerId);
    List<MappedField> mappings = mappings();
    Map<String, String> values = values(customer, mappings);
    // Keep template-only identity data available even when the arrival-table
    // mapping does not expose the field as an editable form control.
    values.putIfAbsent("customerName", text(customer.getCustomerName()));
    values.put("appointmentTime", appointmentTime(customer));
    // Appointment project is not collected in the arrival form. Never send a
    // hidden blank value back to the unique fact database.
    values.remove("appointmentItem");
    if (mappings.stream().anyMatch(mapping -> "assignedKeeper".equals(mapping.targetField()))) {
      values.put("assignedKeeper", currentAccountName(customer));
    }
    return new ManualAppointmentForm(customerId, version(customer), text(customer.getNickname()), text(customer.getPhone()),
        values, formFields(mappings));
  }

  @Transactional
  public ManualAppointmentResult save(long customerId, ManualAppointmentRequest request) {
    Customer customer = require(customerId);
    if (request == null) throw new IllegalArgumentException("预约表单不能为空");
    List<MappedField> mappings = mappings();
    Map<String, String> incoming = new LinkedHashMap<>(request.values());
    if (mappings.stream().anyMatch(mapping -> "assignedKeeper".equals(mapping.targetField()))) {
      incoming.put("assignedKeeper", currentAccountName(customer));
    }
    Map<String, Object> changed = changedValues(customer, mappings, values(customer, mappings), incoming);
    if (!same(text(customer.getAppointmentStatus()), "已预约")) changed.put("appointmentStatus", "已预约");
    int customerVersion = version(customer);
    if (!changed.isEmpty()) customerVersion = writer.write(customer.getPhone(), changed, request.customerVersion(), true,
        CustomerFieldHistoryContext.of("人工预约", "预约表单", AuthContext.username()));
    Customer current = customers.getById(customerId);
    if (current == null) throw new IllegalStateException("预约资料保存后未找到客户");
    ArrivalHandoverTask task = task(current, mappings);
    long taskId = tasks.findManualMatching(current.getPhone(), task.getAppointmentDate(), task.getAppointmentTime(),
        task.getAppointmentStore(), task.getAppointmentItem()).map(existing -> {
          task.setId(existing.getId()); task.setWecomRowId(existing.getWecomRowId());
          tasks.updateManual(existing.getId(), task, AuthContext.username()); return existing.getId();
        }).orElseGet(() -> tasks.createManual(task, AuthContext.username()));
    handover.syncOne(tasks.find(taskId).orElseThrow(() -> new IllegalStateException("预约记录保存失败")));
    ArrivalHandoverTask latest = tasks.find(taskId).orElseThrow();
    return result(latest, current, mappings, taskId, customerVersion, changed.keySet());
  }

  @Transactional
  public ArrivalHandoverCompletionResult saveReports(long taskId, ManualAppointmentReportRequest request) {
    ArrivalHandoverTask task = handover.requireTask(taskId);
    Customer customer = require(task.getCustomerId());
    String reportJson = reports.encode(request == null ? List.of() : request.reports());
    if (!same(text(customer.getCustomerReport()), reportJson)) writer.write(customer.getPhone(), Map.of("customerReport", reportJson),
        null, true, CustomerFieldHistoryContext.of("人工预约", "客户报告", AuthContext.username()));
    tasks.updateManualReports(taskId, reportJson);
    handover.syncOne(tasks.find(taskId).orElseThrow());
    ArrivalHandoverTask latest = tasks.find(taskId).orElseThrow();
    return new ArrivalHandoverCompletionResult(true, "SYNCED".equals(latest.getSyncStatus()), latest.getWecomRowId(), latest.getSyncError());
  }

  private ManualAppointmentResult result(ArrivalHandoverTask task, Customer customer, List<MappedField> mappings,
      long taskId, int customerVersion, Set<String> changed) {
    return new ManualAppointmentResult(true, "SYNCED".equals(task.getSyncStatus()), taskId, task.getWecomRowId(),
        task.getSyncError(), customerVersion, changed.stream().map(this::label).toList(), configuredTemplate(),
        templateValues(customer, mappings));
  }
  private Customer require(long id) {
    Customer customer = customers.getById(id);
    if (customer == null) throw new IllegalArgumentException("客户不存在");
    if (!access.canAccess(customer)) throw new IllegalArgumentException("没有该客户的预约权限");
    if (text(customer.getPhone()).isBlank()) throw new IllegalArgumentException("该客户缺少手机号，无法写入预约记录");
    return customer;
  }
  private List<ManualAppointmentField> formFields(List<MappedField> mappings) {
    Map<String, WecomSmartSheetField> catalog = fieldCatalog.visibleFields(arrivalTarget(), timeout());
    List<ManualAppointmentField> fields = new ArrayList<>(mappings.stream()
        .filter(mapping -> !HIDDEN_FORM_FIELDS.contains(mapping.targetField()) && !"appointmentItem".equals(mapping.targetField()))
        .map(mapping -> {
      WecomSmartSheetField remote = catalog.get(mapping.sourceField());
      String type = "customerReport".equals(mapping.targetField()) ? "FIELD_TYPE_IMAGE" : remote == null ? "FIELD_TYPE_TEXT" : remote.type();
      List<String> options = remote == null ? List.of() : List.copyOf(remote.optionIdsByText().keySet());
      return new ManualAppointmentField(mapping.targetField(), mapping.sourceField(), type,
          !"phone".equals(mapping.targetField()) && !"assignedKeeper".equals(mapping.targetField()), options);
    }).toList());
    int dateIndex = -1;
    for (int index = 0; index < fields.size(); index += 1) {
      if ("appointmentDate".equals(fields.get(index).key())) { dateIndex = index; break; }
    }
    fields.add(dateIndex < 0 ? 0 : dateIndex + 1,
        new ManualAppointmentField("appointmentTime", "到店时间", "FIELD_TYPE_TIME", true, List.of()));
    return List.copyOf(fields);
  }
  private List<MappedField> mappings() {
    String sourceTable = "ARRIVAL:" + arrivalTarget().sheetId();
    return jdbc.query("""
        SELECT source_field, target_field FROM datasource_field_mappings
        WHERE source_table=? AND is_enabled=1 ORDER BY id ASC
        """, (rs, row) -> new MappedField(rs.getString("source_field"), rs.getString("target_field")), sourceTable);
  }
  private Map<String, String> values(Customer customer, List<MappedField> mappings) {
    Map<String, String> result = new LinkedHashMap<>();
    for (MappedField mapping : mappings) result.put(mapping.targetField(), value(customer, mapping.targetField()));
    return result;
  }
  private Map<String, String> templateValues(Customer customer, List<MappedField> mappings) {
    Map<String, String> result = values(customer, mappings);
    String appointmentTime = appointmentTime(customer);
    result.put("customerName", text(customer.getCustomerName()));
    result.put("appointmentDate", value(customer, "appointmentDate"));
    result.put("appointmentTime", appointmentTime);
    result.put("appointmentItem", value(customer, "appointmentItem"));
    result.put("appointmentStore", value(customer, "appointmentStore"));
    result.put("appointmentDateTime", appointmentTime.isBlank() ? "" : value(customer, "appointmentDateTime"));
    return result;
  }
  private Map<String, Object> changedValues(Customer customer, List<MappedField> mappings, Map<String, String> before, Map<String, String> incoming) {
    Map<String, Object> changed = new LinkedHashMap<>(); Set<String> seen = new HashSet<>();
    for (MappedField mapping : mappings) {
      String key = mapping.targetField();
      if (!seen.add(key) || !incoming.containsKey(key) || "customerReport".equals(key)) continue;
      String submitted = text(incoming.get(key));
      if ("phone".equals(key)) {
        if (!same(before.get(key), submitted)) throw new IllegalArgumentException("手机号用于匹配当前客户，不能在预约表单中修改");
      } else if ("appointmentItem".equals(key) || "appointmentTime".equals(key)) {
        continue;
      } else if ("appointmentDateTime".equals(key)) {
        applyAppointmentDateTime(changed, customer, submitted);
      } else if (profileFields.supports(key) && !same(before.get(key), submitted)) {
        changed.put(key, dateValueOrText(key, submitted));
      }
    }
    applyAppointmentTime(changed, customer, incoming);
    return changed;
  }
  private void applyAppointmentTime(Map<String, Object> changed, Customer customer, Map<String, String> incoming) {
    if (!incoming.containsKey("appointmentTime")) return;
    String submitted = text(incoming.get("appointmentTime"));
    if (submitted.isBlank()) {
      if (!text(customer.getAppointmentTime()).isBlank()) changed.put("appointmentTime", "");
      return;
    }
    LocalTime value;
    try { value = parseAppointmentTime(submitted); } catch (DateTimeParseException ex) { throw new IllegalArgumentException("到店时间格式不正确"); }
    String formatted = value.format(DateTimeFormatter.ofPattern("HH:mm"));
    if (!same(text(customer.getAppointmentTime()), formatted)) changed.put("appointmentTime", formatted);
  }
  private void applyAppointmentDateTime(Map<String, Object> changed, Customer customer, String submitted) {
    if (submitted.isBlank()) {
      if (customer.getAppointmentDate() != null) changed.put("appointmentDate", null);
      if (!text(customer.getAppointmentTime()).isBlank()) changed.put("appointmentTime", "");
      return;
    }
    LocalDateTime value;
    try { value = LocalDateTime.parse(submitted); } catch (DateTimeParseException ex) { throw new IllegalArgumentException("首次预约时间格式不正确"); }
    if (!same(value(customer, "appointmentDate"), value.toLocalDate().toString())) changed.put("appointmentDate", value.toLocalDate());
    if (!same(text(customer.getAppointmentTime()), value.toLocalTime().toString())) changed.put("appointmentTime", value.toLocalTime().toString());
  }
  private LocalTime parseAppointmentTime(String submitted) {
    String normalized = submitted.trim().replace('：', ':').replaceAll("\\s+", "");
    if (normalized.startsWith("上午")) return LocalTime.parse(normalized.substring(2), DateTimeFormatter.ofPattern("h:mm"));
    if (normalized.startsWith("下午")) {
      LocalTime time = LocalTime.parse(normalized.substring(2), DateTimeFormatter.ofPattern("h:mm"));
      return time.plusHours(time.getHour() == 12 ? 0 : 12);
    }
    return LocalTime.parse(normalized, DateTimeFormatter.ofPattern("H:mm"));
  }
  private Object dateValueOrText(String key, String value) {
    ProfileFieldRegistry.FieldSpec spec = profileFields.spec(key);
    if (value.isBlank() && (spec.type() == LocalDate.class || spec.type() == LocalDateTime.class)) return null;
    return value;
  }
  private ArrivalHandoverTask task(Customer customer, List<MappedField> mappings) {
    ArrivalHandoverTask task = new ArrivalHandoverTask(); task.setCustomerId(customer.getId()); task.setPhone(customer.getPhone()); task.setAssignedKeeper(customer.getAssignedKeeper());
    task.setAppointmentDate(customer.getAppointmentDate()); task.setAppointmentTime(customer.getAppointmentTime()); task.setAppointmentStore(customer.getAppointmentStore()); task.setAppointmentItem(customer.getAppointmentItem());
    task.setVisitType(valueForLabel(customer, mappings, "类型")); task.setVoucherRedeemed(valueForLabel(customer, mappings, "是否核券")); task.setExperienceProject(valueForLabel(customer, mappings, "体验项目"));
    task.setProjectType(valueForLabel(customer, mappings, "项目类型")); task.setHistoricalExperienceCount(valueForLabel(customer, mappings, "历史体验次数")); task.setCustomerReport(text(customer.getCustomerReport())); return task;
  }
  private String valueForLabel(Customer customer, List<MappedField> mappings, String label) { return mappings.stream().filter(mapping -> label.equals(mapping.sourceField())).findFirst().map(mapping -> value(customer, mapping.targetField())).orElse(""); }
  private String value(Customer customer, String key) {
    if ("phone".equals(key)) return text(customer.getPhone());
    if ("appointmentDateTime".equals(key)) {
      if (customer.getAppointmentDate() == null) return "";
      String time = text(customer.getAppointmentTime());
      return time.isBlank() ? customer.getAppointmentDate() + "T00:00" : customer.getAppointmentDate() + "T" + time.substring(0, Math.min(5, time.length()));
    }
    Object raw = profileFields.readValue(customer, key);
    if (raw instanceof LocalDateTime time) return FORM_DATE_TIME.format(time);
    return raw == null ? "" : String.valueOf(raw);
  }
  private static String appointmentTime(Customer customer) {
    String value = text(customer.getAppointmentTime());
    // Older date-only submissions materialized midnight. It is not a real
    // appointment time and must be collected again instead of shown as one.
    return "00:00".equals(value) ? "" : value;
  }
  private String label(String field) { return mappings().stream().filter(mapping -> mapping.targetField().equals(field)).findFirst().map(MappedField::sourceField).orElse("appointmentStatus".equals(field) ? "预约状态" : field); }
  private String currentAccountName(Customer customer) {
    AuthUser user = AuthContext.current();
    if (user == null) return text(customer.getAssignedKeeper());
    return text(user.displayName()).isBlank() ? text(user.username()) : text(user.displayName());
  }
  private AuxiliarySmartSheetTarget arrivalTarget() { return targets.arrival().orElseThrow(() -> new IllegalStateException("到店表尚未配置")); }
  private Duration timeout() { return Duration.ofMillis(tableConfig.get().writeTimeoutMs()); }
  private String configuredTemplate() { return configs.findValue("arrival.appointment_success_template_id").flatMap(value -> { try { return quickSearch.findEnabledTemplate(Long.parseLong(value.trim())); } catch (NumberFormatException ex) { return java.util.Optional.empty(); } }).map(item -> item.content()).orElse(null); }
  private static int version(Customer customer) { return customer.getVersion() == null ? 0 : customer.getVersion(); }
  private static String text(String value) { return value == null ? "" : value.trim(); }
  private static boolean same(String left, String right) { return text(left).equals(text(right)); }
  private record MappedField(String sourceField, String targetField) {}
}
