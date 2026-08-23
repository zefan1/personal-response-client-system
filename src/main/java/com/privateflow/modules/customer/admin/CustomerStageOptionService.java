package com.privateflow.modules.customer.admin;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetField;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetFieldCatalog;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerStageOptionService {

  private static final String FIELD_TARGET = "customerStage";
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

  private final DatasourceAdminRepository datasourceRepository;
  private final CustomerStageOptionRepository optionRepository;
  private final CustomerRepository customerRepository;
  private final WecomSmartSheetFieldCatalog fieldCatalog;
  private final WecomSmartSheetConfig smartSheetConfig;
  private final AuxiliarySmartSheetTargets auxiliaryTargets;
  private final AuditLogger auditLogger;

  public CustomerStageOptionService(
      DatasourceAdminRepository datasourceRepository,
      CustomerStageOptionRepository optionRepository,
      CustomerRepository customerRepository,
      WecomSmartSheetFieldCatalog fieldCatalog,
      WecomSmartSheetConfig smartSheetConfig,
      AuxiliarySmartSheetTargets auxiliaryTargets,
      AuditLogger auditLogger) {
    this.datasourceRepository = datasourceRepository;
    this.optionRepository = optionRepository;
    this.customerRepository = customerRepository;
    this.fieldCatalog = fieldCatalog;
    this.smartSheetConfig = smartSheetConfig;
    this.auxiliaryTargets = auxiliaryTargets;
    this.auditLogger = auditLogger;
  }

  public Map<String, Object> current(long datasourceId) {
    Datasource datasource = datasource(datasourceId);
    String fieldName = stageField(datasource);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("datasourceId", datasourceId);
    result.put("sourceTable", datasource.sourceTable());
    result.put("fieldName", fieldName);
    result.put("options", optionRepository.list(datasource.sourceTable(), fieldName));
    return result;
  }

  /** Normalizes one internal stage value against the latest confirmed WeCom options. */
  public String normalize(String sourceTable, String value) {
    if (value == null || value.isBlank() || sourceTable == null || sourceTable.isBlank()) {
      return value;
    }
    List<Map<String, Object>> options = optionRepository.list(sourceTable, mappedField(sourceTable));
    List<String> active = options.stream()
        .filter(item -> "ACTIVE".equals(String.valueOf(item.get("status"))))
        .map(item -> String.valueOf(item.get("optionText")))
        .distinct()
        .toList();
    if (active.isEmpty() || active.contains(value.trim())) {
      return value.trim();
    }
    String candidate = value.trim();
    List<String> fuzzy = active.stream()
        .filter(option -> option.contains(candidate) || candidate.contains(option))
        .toList();
    if (fuzzy.size() == 1) {
      return fuzzy.get(0);
    }
    throw new IllegalArgumentException("客户阶段不在当前企业微信选项中：" + candidate + "，可选值为：" + String.join("、", active));
  }

  public String normalizeForCustomer(Customer customer, String value) {
    if (customer == null) {
      return value;
    }
    // Assignment and arrival rows project into the customer master. Their
    // stage must use the master's select options, otherwise a label such as
    // "无效" can pass the local database write but be rejected by WeCom as
    // "无效线索".
    String sourceTable = smartSheetConfig == null || smartSheetConfig.sourceTable().isBlank()
        ? customer.getSourceTable() : smartSheetConfig.sourceTable();
    return normalizeWithRefresh(sourceTable, value);
  }

  public String normalizeByPhone(String phone, String value) {
    if (phone == null || phone.isBlank()) {
      return value;
    }
    return customerRepository.findByPhone(phone)
        .map(customer -> normalizeForCustomer(customer, value))
        .orElse(value == null ? null : value.trim());
  }

  private String normalizeWithRefresh(String sourceTable, String value) {
    if (value == null || value.isBlank() || sourceTable == null || sourceTable.isBlank()) {
      return value;
    }
    String fieldName = mappedField(sourceTable);
    if (!optionRepository.hasActive(sourceTable, fieldName)) {
      datasourceRepository.findBySourceTable(sourceTable).ifPresent(datasource -> {
        try {
          refresh(datasource.id());
        } catch (RuntimeException ex) {
          // Keep the original write error when the live option catalog cannot
          // be read. The caller will queue it instead of silently changing the
          // authoritative value.
        }
      });
    }
    return normalize(sourceTable, value);
  }

  @Transactional
  public Map<String, Object> refresh(long datasourceId) {
    Datasource datasource = datasource(datasourceId);
    String fieldName = stageField(datasource);
    AuxiliarySmartSheetTarget target = target(datasource);
    if (target == null || !target.configured()) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "客户主表的企业微信连接尚未完整配置");
    }
    WecomSmartSheetField field = fieldCatalog.visibleFields(target, READ_TIMEOUT).get(fieldName);
    if (field == null) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "客户阶段列未出现在企业微信客户主表中：" + fieldName);
    }
    if (!"FIELD_TYPE_SINGLE_SELECT".equals(field.type()) && !"FIELD_TYPE_SELECT".equals(field.type())) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "客户阶段列不是单选或多选字段，无法同步阶段选项");
    }
    boolean initial = !optionRepository.exists(datasource.sourceTable(), fieldName);
    List<String> optionIds = new ArrayList<>();
    field.optionIdsByText().forEach((text, id) -> {
      optionIds.add(id);
      optionRepository.observe(datasource.sourceTable(), fieldName, id, text, initial);
    });
    optionRepository.markMissing(datasource.sourceTable(), fieldName, optionIds);
    Map<String, Object> result = current(datasourceId);
    result.put("remoteOptionCount", optionIds.size());
    result.put("refreshed", true);
    auditLogger.log("CUSTOMER_STAGE_OPTIONS_REFRESH", AuthContext.username(), "DATASOURCE",
        Long.toString(datasourceId), "sourceTable=" + datasource.sourceTable() + ", options=" + optionIds.size());
    return result;
  }

  @Transactional
  public Map<String, Object> decide(long datasourceId, CustomerStageOptionDecisionRequest request) {
    if (request == null || blank(request.oldOptionId()) || blank(request.newOptionId()) || blank(request.decision())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "阶段选项确认信息不完整");
    }
    Datasource datasource = datasource(datasourceId);
    String fieldName = stageField(datasource);
    Map<String, Object> oldOption = optionRepository.find(datasource.sourceTable(), fieldName, request.oldOptionId().trim())
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "旧阶段选项不存在"));
    Map<String, Object> newOption = optionRepository.find(datasource.sourceTable(), fieldName, request.newOptionId().trim())
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "新阶段选项不存在"));
    String decision = request.decision().trim().toUpperCase();
    int migrated = 0;
    if ("SAME".equals(decision)) {
      String oldText = String.valueOf(oldOption.get("optionText"));
      String newText = String.valueOf(newOption.get("optionText"));
      migrated = customerRepository.replaceCustomerStage(oldText, newText);
      optionRepository.confirm(datasource.sourceTable(), fieldName, request.oldOptionId().trim(), "MIGRATED", AuthContext.username());
      optionRepository.confirm(datasource.sourceTable(), fieldName, request.newOptionId().trim(), "ACTIVE", AuthContext.username());
    } else if ("NEW".equals(decision)) {
      optionRepository.confirm(datasource.sourceTable(), fieldName, request.newOptionId().trim(), "ACTIVE", AuthContext.username());
    } else {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "不支持的阶段选项确认方式");
    }
    auditLogger.log("CUSTOMER_STAGE_OPTION_DECISION", AuthContext.username(), "DATASOURCE",
        Long.toString(datasourceId), "decision=" + decision + ", old=" + request.oldOptionId() + ", new=" + request.newOptionId());
    Map<String, Object> result = current(datasourceId);
    result.put("decision", decision);
    result.put("migratedCustomers", migrated);
    return result;
  }

  private Datasource datasource(long id) {
    return datasourceRepository.find(id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "datasource not found"));
  }

  private String stageField(Datasource datasource) {
    return datasourceRepository.mappings(datasource.sourceTable()).stream()
        .filter(mapping -> mapping.enabled() && FIELD_TARGET.equals(mapping.targetField()))
        .map(FieldMappingDto::sourceField)
        .findFirst()
        .orElseThrow(() -> new ApiException(ApiErrorCodes.CONFLICT, "请先将企业微信客户阶段列映射为系统内容“客户阶段”"));
  }

  private String mappedField(String sourceTable) {
    return datasourceRepository.mappings(sourceTable).stream()
        .filter(mapping -> mapping.enabled() && FIELD_TARGET.equals(mapping.targetField()))
        .map(FieldMappingDto::sourceField)
        .findFirst()
        .orElse("客户阶段");
  }

  private AuxiliarySmartSheetTarget target(Datasource datasource) {
    if (smartSheetConfig != null && datasource.sheetId().equals(smartSheetConfig.documentId())
        && datasource.sourceTable().equals(smartSheetConfig.sourceTable())) {
      return new AuxiliarySmartSheetTarget("PRIMARY", smartSheetConfig.documentId(), smartSheetConfig.sheetId(),
          smartSheetConfig.viewId(), smartSheetConfig.uniqueFieldTitle(), "");
    }
    if (auxiliaryTargets == null) {
      return null;
    }
    if (datasource.sourceTable().startsWith("ASSIGNMENT:")) {
      return auxiliaryTargets.assignment().orElse(null);
    }
    if (datasource.sourceTable().startsWith("ARRIVAL:")) {
      return auxiliaryTargets.arrival().orElse(null);
    }
    return null;
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
