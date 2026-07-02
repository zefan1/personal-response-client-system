package com.privateflow.modules.api.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

  private static final List<String> ACTIONS = List.of(
      "CALL_SKILL", "COPY_REPLY", "SEND_MESSAGE", "BATCH_TEMPLATE",
      "UPDATE_PROFILE", "UPDATE_STAGE", "UPDATE_TAG", "SAVE_TO_TABLE",
      "ASK_FOR_HELP", "RESOLVE_HELP", "UPDATE_CONFIG",
      "CREATE_NOTICE", "STOP_NOTICE", "PUBLISH_NOTICE",
      "VERSION_PUBLISH", "VERSION_REVOKE",
      "DATASOURCE_CREATE", "DATASOURCE_UPDATE", "DATASOURCE_DELETE",
      "ACCOUNT_CREATE", "ACCOUNT_UPDATE", "ACCOUNT_DELETE", "ACCOUNT_TOGGLE");

  private final AuditLogRepository repository;
  private final SystemConfigRepository configRepository;
  private final ObjectMapper objectMapper;
  private final Executor apiOrchestrationExecutor;

  public AuditLogService(
      AuditLogRepository repository,
      SystemConfigRepository configRepository,
      ObjectMapper objectMapper,
      @Qualifier("apiOrchestrationExecutor") Executor apiOrchestrationExecutor) {
    this.repository = repository;
    this.configRepository = configRepository;
    this.objectMapper = objectMapper;
    this.apiOrchestrationExecutor = apiOrchestrationExecutor;
  }

  public Map<String, Object> list(AuditLogQuery query) {
    requireAdmin();
    AuditLogQuery normalized = normalize(query);
    long total = repository.count(normalized);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("items", repository.list(normalized).stream().map(this::toResponse).toList());
    result.put("total", total);
    result.put("page", normalized.page());
    result.put("size", normalized.size());
    result.put("totalPages", (total + normalized.size() - 1) / normalized.size());
    result.put("earliestCreatedAt", repository.earliest().map(LocalDateTime::toString).orElse(null));
    result.put("retentionDays", intConfig("system.audit_log_retention_days", 90));
    return result;
  }

  public Map<String, Object> actions() {
    requireAdmin();
    return Map.of(
        "actions", ACTIONS.stream().map(action -> Map.of(
            "action", action,
            "label", actionLabel(action),
            "group", actionGroup(action))).toList(),
        "targetTypes", List.of(
            target("customer", "客户"),
            target("config", "系统配置"),
            target("account", "账号"),
            target("notice", "公告"),
            target("version", "版本"),
            target("datasource", "数据源"),
            target("help", "求助"),
            target("skill", "Skill 调用"),
            target("template", "模板/速搜")));
  }

  public Map<String, Object> export(AuditLogQuery query) {
    requireAdmin();
    AuditLogQuery normalized = normalizeForExport(query);
    long total = repository.count(normalized);
    int maxRows = intConfig("audit.export_max_rows", 10000);
    if (total > maxRows) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "export data too large (" + total + "), narrow the time range and retry");
    }
    String exportId = "exp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    LocalDateTime expireAt = LocalDateTime.now().plusHours(intConfig("audit.export_cos_retention_hours", 168));
    repository.createExport(exportId, toJson(normalized), total, expireAt, AuthContext.username());
    apiOrchestrationExecutor.execute(() -> generateExport(exportId, normalized, maxRows));
    return Map.of(
        "exportId", exportId,
        "status", AuditExportStatus.PROCESSING.name(),
        "totalCount", total,
        "message", "正在生成 CSV 文件，请稍后查看下载链接");
  }

  public Map<String, Object> exportStatus(String exportId) {
    requireAdmin();
    AuditExportRecord record = requireExport(exportId);
    if (record.status() == AuditExportStatus.PROCESSING
        && record.createdAt().plusSeconds(intConfig("audit.export_timeout_seconds", 120)).isBefore(LocalDateTime.now())) {
      repository.failExport(exportId, "CSV 生成失败：导出任务超时");
      record = requireExport(exportId);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("exportId", record.exportId());
    result.put("status", record.status().name());
    result.put("message", record.message());
    result.put("totalCount", record.totalCount());
    result.put("downloadUrl", record.downloadUrl());
    result.put("expireAt", record.expireAt());
    result.put("terminal", record.status() == AuditExportStatus.COMPLETED || record.status() == AuditExportStatus.FAILED);
    return result;
  }

  public String downloadCsv(String exportId) {
    requireAdmin();
    AuditExportRecord record = requireExport(exportId);
    if (record.status() != AuditExportStatus.COMPLETED || record.csvContent() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "export is not ready");
    }
    if (record.expireAt().isBefore(LocalDateTime.now())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "export link expired");
    }
    return record.csvContent();
  }

  @Scheduled(cron = "0 0 4 * * *")
  public void cleanupAuditLogs() {
    int retentionDays = intConfig("system.audit_log_retention_days", 90);
    int batchSize = intConfig("system.audit_log_cleanup_batch_size", 5000);
    while (repository.cleanupAuditLogs(retentionDays, batchSize) == batchSize) {
      // Keep deleting in bounded batches until the old range is empty.
    }
    repository.cleanupExports(intConfig("audit.export_cos_retention_hours", 168));
  }

  private void generateExport(String exportId, AuditLogQuery query, int maxRows) {
    try {
      List<AuditLogEntry> rows = repository.exportRows(query, maxRows);
      String csv = toCsv(rows);
      repository.completeExport(exportId, csv, "/admin/api/v1/audit-logs/export/" + exportId + "/download");
    } catch (RuntimeException ex) {
      repository.failExport(exportId, "CSV 生成失败：" + ex.getMessage());
    }
  }

  private AuditLogQuery normalize(AuditLogQuery query) {
    AuditLogQuery actual = query == null ? emptyQuery() : query;
    LocalDate start = actual.startDate() == null ? LocalDate.now().minusDays(7) : actual.startDate();
    LocalDate end = actual.endDate() == null ? LocalDate.now() : actual.endDate();
    if (end.isBefore(start)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "endDate must be after startDate");
    }
    int maxSize = intConfig("audit.list_max_page_size", 100);
    int size = Math.max(10, Math.min(actual.size() <= 0 ? intConfig("audit.list_page_size_default", 20) : actual.size(), maxSize));
    validateActions(actual.actions());
    return new AuditLogQuery(actual.actions(), clean(actual.operator()), clean(actual.targetType()), clean(actual.targetId()),
        clean(actual.keyword()), start, end, Math.max(1, actual.page()), size);
  }

  private AuditLogQuery normalizeForExport(AuditLogQuery query) {
    AuditLogQuery normalized = normalize(query);
    return new AuditLogQuery(normalized.actions(), normalized.operator(), normalized.targetType(), normalized.targetId(),
        normalized.keyword(), normalized.startDate(), normalized.endDate(), 1, intConfig("audit.export_max_rows", 10000));
  }

  private void validateActions(List<String> actions) {
    if (actions == null) {
      return;
    }
    for (String action : actions) {
      if (!ACTIONS.contains(action)) {
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, "unknown action " + action);
      }
    }
  }

  private Map<String, Object> toResponse(AuditLogEntry entry) {
    Object parsed = parseDetail(entry.detail());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entry.id());
    result.put("operator", mask(entry.operator()));
    result.put("action", entry.action());
    result.put("actionLabel", actionLabel(entry.action()));
    result.put("actionGroup", actionGroup(entry.action()));
    result.put("targetType", entry.targetType());
    result.put("targetTypeLabel", targetTypeLabel(entry.targetType()));
    result.put("targetId", entry.targetId());
    result.put("detail", entry.detail());
    result.put("detailParsed", parsed);
    result.put("detailSummary", detailSummary(entry, parsed));
    result.put("createdAt", entry.createdAt());
    return result;
  }

  private Object parseDetail(String detail) {
    if (detail == null || detail.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(detail, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ex) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private String detailSummary(AuditLogEntry entry, Object parsed) {
    if (parsed instanceof Map<?, ?> raw) {
      Map<String, Object> detail = (Map<String, Object>) raw;
      return switch (entry.action()) {
        case "UPDATE_STAGE" -> "客户阶段：" + value(detail, "fromStage", "from") + " -> " + value(detail, "toStage", "to");
        case "UPDATE_TAG" -> value(detail, "tagName", "tag") + ": " + value(detail, "operation", "action");
        case "UPDATE_PROFILE" -> "更新客户档案：" + value(detail, "fields", "updatedFields");
        case "UPDATE_CONFIG" -> "修改配置：" + value(detail, "key", "configKey") + " " + value(detail, "oldValue", "from") + " -> " + value(detail, "newValue", "to");
        case "CALL_SKILL" -> value(detail, "sceneLabel", "scene") + " · " + value(detail, "success", "status");
        case "COPY_REPLY" -> "复制回复：" + value(detail, "direction", "type");
        case "SEND_MESSAGE" -> "发送消息：" + last4(value(detail, "phone", "targetId"));
        case "SAVE_TO_TABLE" -> "保存到表格：" + value(detail, "updatedFields", "fields");
        case "ASK_FOR_HELP" -> "向组长 " + mask(value(detail, "targetLeader", "leader")) + " 发起求助";
        case "RESOLVE_HELP" -> "组长回复了 " + value(detail, "replyCount", "count") + " 条建议";
        case "BATCH_TEMPLATE" -> "批量模板 " + value(detail, "templateName", "template") + " 发送 " + value(detail, "count", "total") + " 条";
        case "CREATE_NOTICE" -> "创建公告：" + value(detail, "title", "noticeTitle");
        case "STOP_NOTICE" -> "停止公告：" + value(detail, "title", "noticeTitle");
        case "PUBLISH_NOTICE" -> "发布公告：" + value(detail, "title", "noticeTitle");
        case "VERSION_PUBLISH" -> "发布版本 " + value(detail, "version", "targetId") + " (" + value(detail, "platform", "targetType") + ")";
        case "VERSION_REVOKE" -> "撤回版本 " + value(detail, "version", "targetId") + " (" + value(detail, "platform", "targetType") + ")";
        default -> fallback(entry.detail());
      };
    }
    return fallback(entry.detail());
  }

  private String toCsv(List<AuditLogEntry> rows) {
    StringBuilder csv = new StringBuilder("\uFEFF操作时间,操作人,操作类型,操作对象,操作摘要,详情\n");
    rows.stream().map(this::toResponse).forEach(row -> csv.append(csv(row.get("createdAt")))
        .append(',').append(csv(row.get("operator")))
        .append(',').append(csv(row.get("actionLabel")))
        .append(',').append(csv(row.get("targetTypeLabel") + ":" + row.get("targetId")))
        .append(',').append(csv(row.get("detailSummary")))
        .append(',').append(csv(row.get("detail")))
        .append('\n'));
    return csv.toString();
  }

  private String csv(Object value) {
    String text = value == null ? "" : String.valueOf(value);
    return "\"" + text.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
  }

  private String actionLabel(String action) {
    return switch (action) {
      case "CALL_SKILL" -> "调用 Skill";
      case "COPY_REPLY" -> "复制回复";
      case "SEND_MESSAGE" -> "发送消息";
      case "BATCH_TEMPLATE" -> "批量模板发送";
      case "UPDATE_PROFILE" -> "更新客户档案";
      case "UPDATE_STAGE" -> "更新客户阶段";
      case "UPDATE_TAG" -> "更新客户标签";
      case "SAVE_TO_TABLE" -> "保存到表格";
      case "ASK_FOR_HELP" -> "发起求助";
      case "RESOLVE_HELP" -> "解决求助";
      case "UPDATE_CONFIG" -> "修改系统配置";
      case "CREATE_NOTICE" -> "创建公告";
      case "STOP_NOTICE" -> "停止公告";
      case "PUBLISH_NOTICE" -> "发布公告";
      case "VERSION_PUBLISH" -> "发布版本";
      case "VERSION_REVOKE" -> "撤回版本";
      case "DATASOURCE_CREATE" -> "新增数据源";
      case "DATASOURCE_UPDATE" -> "编辑数据源";
      case "DATASOURCE_DELETE" -> "删除数据源";
      case "ACCOUNT_CREATE" -> "新增账号";
      case "ACCOUNT_UPDATE" -> "编辑账号";
      case "ACCOUNT_DELETE" -> "删除账号";
      case "ACCOUNT_TOGGLE" -> "启用/停用账号";
      default -> action;
    };
  }

  private String actionGroup(String action) {
    return switch (action) {
      case "CALL_SKILL" -> "AI 操作";
      case "COPY_REPLY", "SEND_MESSAGE", "BATCH_TEMPLATE" -> "回复操作";
      case "UPDATE_PROFILE", "UPDATE_STAGE", "UPDATE_TAG", "SAVE_TO_TABLE" -> "客户操作";
      case "ASK_FOR_HELP", "RESOLVE_HELP" -> "求助操作";
      case "UPDATE_CONFIG" -> "配置操作";
      case "CREATE_NOTICE", "STOP_NOTICE", "PUBLISH_NOTICE" -> "公告操作";
      case "VERSION_PUBLISH", "VERSION_REVOKE" -> "版本操作";
      case "DATASOURCE_CREATE", "DATASOURCE_UPDATE", "DATASOURCE_DELETE" -> "数据源操作";
      case "ACCOUNT_CREATE", "ACCOUNT_UPDATE", "ACCOUNT_DELETE", "ACCOUNT_TOGGLE" -> "账号操作";
      default -> "其他";
    };
  }

  private String targetTypeLabel(String type) {
    return switch (type == null ? "" : type) {
      case "customer" -> "客户";
      case "config", "system_configs" -> "系统配置";
      case "account" -> "账号";
      case "notice", "system_notices" -> "公告";
      case "version" -> "版本";
      case "datasource" -> "数据源";
      case "help" -> "求助";
      case "skill" -> "Skill 调用";
      case "template" -> "模板/速搜";
      default -> type == null ? "" : type;
    };
  }

  private Map<String, String> target(String type, String label) {
    return Map.of("type", type, "label", label);
  }

  private String value(Map<String, Object> detail, String first, String second) {
    Object value = detail.containsKey(first) ? detail.get(first) : detail.get(second);
    return value == null ? "" : String.valueOf(value);
  }

  private String fallback(String detail) {
    if (detail == null) {
      return "";
    }
    return detail.length() <= 50 ? detail : detail.substring(0, 50);
  }

  private String last4(String value) {
    if (value == null || value.length() <= 4) {
      return value == null ? "" : value;
    }
    return value.substring(value.length() - 4);
  }

  private String mask(String value) {
    if (value == null || value.isBlank() || "SYSTEM".equalsIgnoreCase(value)) {
      return value;
    }
    String trimmed = value.trim();
    if (trimmed.length() < 7) {
      return trimmed;
    }
    return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
  }

  private String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private AuditLogQuery emptyQuery() {
    return new AuditLogQuery(List.of(), null, null, null, null, null, null, 1, 20);
  }

  private AuditExportRecord requireExport(String exportId) {
    if (exportId == null || exportId.isBlank()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "exportId is required");
    }
    return repository.findExport(exportId).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "export not found"));
  }

  private void requireAdmin() {
    AuthUser user = AuthContext.current();
    if (user == null || user.role() != Role.ADMIN) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "permission denied");
    }
  }

  private int intConfig(String key, int fallback) {
    return configRepository.findValue(key).map(value -> {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException ex) {
        return fallback;
      }
    }).orElse(fallback);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      return "{}";
    }
  }
}
