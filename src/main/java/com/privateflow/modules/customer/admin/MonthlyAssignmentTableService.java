package com.privateflow.modules.customer.admin;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetProvisioningService;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class MonthlyAssignmentTableService {

  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
  private final MonthlyAssignmentTableRepository repository;
  private final AuxiliarySmartSheetTargets targets;
  private final WecomSmartSheetProvisioningService provisioningService;
  private final MonthlyAssignmentTableActivationService activationService;
  private final AuditLogger auditLogger;
  private final Clock clock;

  @Autowired
  public MonthlyAssignmentTableService(
      MonthlyAssignmentTableRepository repository,
      AuxiliarySmartSheetTargets targets,
      WecomSmartSheetProvisioningService provisioningService,
      MonthlyAssignmentTableActivationService activationService,
      AuditLogger auditLogger) {
    this(repository, targets, provisioningService, activationService, auditLogger,
        Clock.system(BUSINESS_ZONE));
  }

  MonthlyAssignmentTableService(
      MonthlyAssignmentTableRepository repository,
      AuxiliarySmartSheetTargets targets,
      WecomSmartSheetProvisioningService provisioningService,
      MonthlyAssignmentTableActivationService activationService,
      AuditLogger auditLogger,
      Clock clock) {
    this.repository = Objects.requireNonNull(repository);
    this.targets = Objects.requireNonNull(targets);
    this.provisioningService = Objects.requireNonNull(provisioningService);
    this.activationService = Objects.requireNonNull(activationService);
    this.auditLogger = Objects.requireNonNull(auditLogger);
    this.clock = Objects.requireNonNull(clock);
  }

  public List<MonthlyAssignmentTable> list() {
    return repository.list(24);
  }

  public MonthlyAssignmentTable create(MonthlyAssignmentTableCreateRequest request) {
    String tableName = normalizeName(request == null ? null : request.tableName());
    String monthKey = YearMonth.now(clock).toString();
    MonthlyAssignmentTable existing = repository.findByName(tableName).orElse(null);
    if (existing != null && !"FAILED".equals(existing.status())) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "这个表格名称已经存在，请换一个名称");
    }

    long id;
    if (existing == null) {
      try {
        id = repository.createPending(tableName, monthKey, AuthContext.username());
      } catch (DuplicateKeyException ex) {
        throw new ApiException(ApiErrorCodes.CONFLICT, "这个表格名称已经存在，请换一个名称");
      }
    } else if (!existing.documentId().isBlank() && !existing.documentUrl().isBlank()) {
      id = existing.id();
    } else {
      throw new ApiException(ApiErrorCodes.CONFLICT,
          "这次失败发生在旧版本，系统没有保存企业微信文档链接。请先在企业微信确认同名表格后再重新创建");
    }

    try {
      AuxiliarySmartSheetTarget current = targets.assignment().orElseThrow(
          () -> new ApiException(ApiErrorCodes.CONFIG_INVALID, "当前还没有配置可用的分配表，暂时不能创建新表"));
      WecomSmartSheetProvisioningService.ProvisionedSheet created;
      if (existing != null) {
        created = provisioningService.provisionExistingFromTemplate(
            new WecomSmartSheetProvisioningService.CreatedDocument(existing.documentId(), existing.documentUrl()), current);
      } else {
        created = provisioningService.provisionFromTemplate(tableName, current,
            document -> repository.markDocumentCreated(id, document.documentId(), document.documentUrl()));
      }
      repository.markReady(id, created.documentId(), created.sheetId(), created.viewId(),
          created.uniqueFieldTitle(), created.documentUrl());
      activationService.activate(id, created);
    } catch (RuntimeException ex) {
      repository.markFailed(id, userMessage(ex));
      if (ex instanceof ApiException apiException) {
        throw apiException;
      }
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, userMessage(ex));
    }
    MonthlyAssignmentTable result = repository.findById(id).orElseThrow();
    auditLogger.log("ASSIGNMENT_TABLE_CREATE", AuthContext.username(), "monthly_assignment_tables",
        String.valueOf(id), "created and activated " + tableName);
    return result;
  }

  public MonthlyAssignmentTable rebind(long id) {
    MonthlyAssignmentTable table = repository.findById(id).orElseThrow(
        () -> new ApiException(ApiErrorCodes.BAD_REQUEST, "未找到这条历史分配表记录"));
    if ("ACTIVE".equals(table.status())) {
      return table;
    }
    if (table.documentId().isBlank() || table.sheetId().isBlank() || table.viewId().isBlank()
        || table.documentUrl().isBlank() || table.uniqueFieldTitle().isBlank()) {
      throw new ApiException(ApiErrorCodes.CONFLICT,
          "这条记录没有完整绑定信息，无法换绑；可删除无效记录后重新创建分配表");
    }
    activationService.activateExisting(table);
    MonthlyAssignmentTable result = repository.findById(id).orElseThrow();
    auditLogger.log("ASSIGNMENT_TABLE_REBIND", AuthContext.username(), "monthly_assignment_tables",
        String.valueOf(id), "rebound and activated " + table.tableName());
    return result;
  }

  public void delete(long id) {
    MonthlyAssignmentTable table = repository.findById(id).orElseThrow(
        () -> new ApiException(ApiErrorCodes.BAD_REQUEST, "未找到这条历史分配表记录"));
    if ("ACTIVE".equals(table.status())) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "当前正在使用的分配表不能删除，请先换绑到其他表格");
    }
    repository.delete(id);
    auditLogger.log("ASSIGNMENT_TABLE_DELETE", AuthContext.username(), "monthly_assignment_tables",
        String.valueOf(id), "deleted local history record " + table.tableName());
  }

  private String normalizeName(String value) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isBlank()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请先填写分配表名称");
    }
    if (normalized.length() > 200) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "分配表名称不能超过 200 个字符");
    }
    return normalized;
  }

  private String userMessage(RuntimeException ex) {
    if (ex instanceof ApiException) return ex.getMessage();
    return ex.getMessage() == null || ex.getMessage().isBlank()
        ? "创建分配表失败，请检查企业微信连接后重试" : ex.getMessage();
  }
}
