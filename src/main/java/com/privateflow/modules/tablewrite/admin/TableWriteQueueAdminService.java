package com.privateflow.modules.tablewrite.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.tablewrite.PendingTableWrite;
import com.privateflow.modules.tablewrite.infra.PendingTableWriteRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableWriteQueueAdminService {

  private static final int DEFAULT_LIST_LIMIT = 50;
  private static final int MAX_LIST_LIMIT = 100;
  private final PendingTableWriteRepository repository;
  private final AuditLogger auditLogger;
  private final ObjectMapper objectMapper;

  public TableWriteQueueAdminService(
      PendingTableWriteRepository repository,
      AuditLogger auditLogger,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.auditLogger = auditLogger;
    this.objectMapper = objectMapper;
  }

  public List<Map<String, Object>> listFailed(int limit) {
    requireAdmin();
    int safeLimit = Math.max(1, Math.min(limit <= 0 ? DEFAULT_LIST_LIMIT : limit, MAX_LIST_LIMIT));
    return repository.failed(safeLimit).stream().map(this::toResponse).toList();
  }

  @Transactional
  public Map<String, Object> requeueFailed(long id) {
    requireAdmin();
    if (id <= 0) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "无效的表格写入记录");
    }
    PendingTableWrite item = repository.findFailed(id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.CONFLICT, "该失败记录已被处理或不存在"));
    if (repository.requeueFailed(id, LocalDateTime.now()) != 1) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "该失败记录已被其他管理员处理");
    }
    audit(item);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("status", "PENDING");
    result.put("message", "已重新加入写入队列，系统将按原数据重试");
    return result;
  }

  @Transactional
  public Map<String, Object> resolveFailed(long id) {
    requireAdmin();
    if (id <= 0) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "无效的表格写入记录");
    }
    PendingTableWrite item = repository.findFailed(id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.CONFLICT, "该失败记录已被处理或不存在"));
    if (repository.resolveFailed(id) != 1) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "该失败记录已被其他管理员处理");
    }
    auditResolved(item);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("status", "RESOLVED");
    result.put("message", "已关闭失败记录，保留原始错误和审计信息，不再自动重试");
    return result;
  }

  private Map<String, Object> toResponse(PendingTableWrite item) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("id", item.getId());
    response.put("customerId", item.getCustomerId());
    response.put("phoneLast4", last4(item.getPhone()));
    response.put("actionType", item.getActionType() == null ? null : item.getActionType().name());
    response.put("retryCount", item.getRetryCount());
    response.put("errorMsg", item.getErrorMsg());
    response.put("createdAt", item.getCreatedAt());
    response.put("updatedAt", item.getUpdatedAt());
    return response;
  }

  private void audit(PendingTableWrite item) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("customerId", item.getCustomerId());
    detail.put("actionType", item.getActionType() == null ? null : item.getActionType().name());
    detail.put("retryCountBefore", item.getRetryCount());
    detail.put("originalError", item.getErrorMsg());
    try {
      auditLogger.log("TABLE_WRITE_REQUEUE", AuthContext.username(), "table_write", String.valueOf(item.getId()),
          objectMapper.writeValueAsString(detail));
    } catch (Exception ex) {
      auditLogger.log("TABLE_WRITE_REQUEUE", AuthContext.username(), "table_write", String.valueOf(item.getId()),
          "重新加入企业微信智能表格写入队列");
    }
  }

  private void auditResolved(PendingTableWrite item) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("customerId", item.getCustomerId());
    detail.put("actionType", item.getActionType() == null ? null : item.getActionType().name());
    detail.put("retryCountBefore", item.getRetryCount());
    detail.put("originalError", item.getErrorMsg());
    try {
      auditLogger.log("TABLE_WRITE_RESOLVE", AuthContext.username(), "table_write", String.valueOf(item.getId()),
          objectMapper.writeValueAsString(detail));
    } catch (Exception ex) {
      auditLogger.log("TABLE_WRITE_RESOLVE", AuthContext.username(), "table_write", String.valueOf(item.getId()),
          "关闭企业微信智能表格失败记录");
    }
  }

  private void requireAdmin() {
    AuthUser user = AuthContext.current();
    if (user == null || user.role() != Role.ADMIN) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "管理员权限不足");
    }
  }

  private String last4(String phone) {
    if (phone == null || phone.isBlank()) {
      return "";
    }
    String value = phone.trim();
    return value.length() <= 4 ? value : value.substring(value.length() - 4);
  }
}
