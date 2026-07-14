package com.privateflow.modules.api.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AccountPermissionRepository;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.auth.PermissionCodes;
import com.privateflow.modules.api.auth.RefreshTokenStore;
import com.privateflow.modules.api.ws.WsPushService;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAdminService {

  private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
  private final AccountAdminRepository repository;
  private final AccountPermissionRepository permissionRepository;
  private final RefreshTokenStore refreshTokenStore;
  private final WsPushService wsPushService;
  private final AuditLogger auditLogger;
  private final ObjectMapper objectMapper;

  public AccountAdminService(
      AccountAdminRepository repository,
      AccountPermissionRepository permissionRepository,
      RefreshTokenStore refreshTokenStore,
      WsPushService wsPushService,
      AuditLogger auditLogger,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.permissionRepository = permissionRepository;
    this.refreshTokenStore = refreshTokenStore;
    this.wsPushService = wsPushService;
    this.auditLogger = auditLogger;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> list(int page, int pageSize, Role role, String keyword, Boolean enabled) {
    int safePage = Math.max(1, page);
    int safePageSize = Math.max(10, Math.min(50, pageSize));
    long total = repository.count(role, keyword, enabled);
    return Map.of(
        "total", total,
        "page", safePage,
        "pageSize", safePageSize,
        "totalPages", Math.max(1, (int) Math.ceil(total / (double) safePageSize)),
        "list", repository.list(safePage, safePageSize, role, keyword, enabled));
  }

  @Transactional
  public AccountAdminItem create(AccountCreateRequest request) {
    validateCreate(request);
    Set<String> permissions = normalizePermissions(request.role(), request.permissions(), Set.of());
    long id = repository.create(new AccountCreateRequest(
        request.phone().trim(),
        request.password(),
        request.displayName().trim(),
        request.role(),
        normalizeLeaderId(request.role(), request.leaderId()),
        permissions),
        BCrypt.hashpw(request.password(), BCrypt.gensalt()));
    permissionRepository.replace(id, permissions, AuthContext.username());
    AccountAdminItem saved = require(id);
    audit("ACCOUNT_CREATE", saved, accountDetail(saved));
    return saved;
  }

  @Transactional
  public AccountAdminItem update(long id, AccountUpdateRequest request) {
    AccountAdminItem current = require(id);
    validateUpdate(request);
    Long leaderId = normalizeLeaderId(request.role(), request.leaderId());
    Set<String> permissions = normalizePermissions(request.role(), request.permissions(), current.permissions());
    AuthUser operator = AuthContext.current();
    if (operator.username().equals(current.phone())
        && (current.role() != request.role() || current.isEnabled() != Boolean.TRUE.equals(request.isEnabled()))) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "不能修改当前登录账号的角色或启停状态");
    }
    repository.update(id, request, leaderId);
    permissionRepository.replace(id, permissions, AuthContext.username());
    boolean securityChanged = current.role() != request.role()
        || current.isEnabled() != Boolean.TRUE.equals(request.isEnabled())
        || !Objects.equals(current.leaderId(), leaderId)
        || !Objects.equals(current.permissions(), permissions);
    if (securityChanged) {
      repository.bumpTokenVersion(id);
      refreshTokenStore.revoke(current.phone());
      wsPushService.invalidateActiveSession(current.phone(),
          Boolean.FALSE.equals(request.isEnabled()) ? "账号已停用，请联系管理员" : "账号权限已变更，请重新登录");
    }
    AccountAdminItem saved = require(id);
    Map<String, Object> detail = accountDetail(saved);
    detail.put("previousRole", current.role().name());
    detail.put("previousEnabled", current.isEnabled());
    audit("ACCOUNT_UPDATE", saved, detail);
    return saved;
  }

  @Transactional
  public AccountAdminItem toggle(long id, boolean enabled) {
    AccountAdminItem current = require(id);
    if (AuthContext.username().equals(current.phone())) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "不能停用当前登录账号");
    }
    repository.toggle(id, enabled);
    if (!enabled) {
      refreshTokenStore.revoke(current.phone());
      wsPushService.invalidateActiveSession(current.phone(), "账号已停用，请联系管理员");
    }
    AccountAdminItem saved = require(id);
    Map<String, Object> detail = accountDetail(saved);
    detail.put("enabledBefore", current.isEnabled());
    detail.put("enabledAfter", enabled);
    audit("ACCOUNT_TOGGLE", saved, detail);
    return saved;
  }

  @Transactional
  public Map<String, Object> resetPassword(long id, PasswordResetRequest request) {
    AccountAdminItem current = require(id);
    if (request == null || request.newPassword() == null || request.newPassword().length() < 6) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "新密码至少需要 6 位");
    }
    repository.resetPassword(id, BCrypt.hashpw(request.newPassword(), BCrypt.gensalt()));
    refreshTokenStore.revoke(current.phone());
    wsPushService.invalidateActiveSession(current.phone(), "密码已重置，请使用新密码重新登录");
    audit("ACCOUNT_RESET_PASSWORD", current, Map.of("id", current.id(), "role", current.role().name(), "revokedRefreshToken", true));
    return Map.of("id", id, "revokedRefreshToken", true);
  }

  @Transactional
  public void delete(long id) {
    AccountAdminItem current = require(id);
    if (AuthContext.username().equals(current.phone())) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "不能删除当前登录账号");
    }
    if (current.role() == Role.LEADER) {
      int keeperCount = repository.enabledKeeperCount(id);
      if (keeperCount > 0) {
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, "该组长名下还有 " + keeperCount + " 个启用中的管家，请先调整直属关系");
      }
    }
    repository.delete(id);
    refreshTokenStore.revoke(current.phone());
    wsPushService.invalidateActiveSession(current.phone(), "账号已删除，请联系管理员");
    audit("ACCOUNT_DELETE", current, accountDetail(current));
  }

  private void validateCreate(AccountCreateRequest request) {
    if (request == null || blank(request.phone()) || !PHONE.matcher(request.phone().trim()).matches()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "手机号格式不正确");
    }
    if (repository.phoneExists(request.phone().trim())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "该手机号已注册");
    }
    if (request.password() == null || request.password().length() < 6) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "密码至少需要 6 位");
    }
    validateDisplayName(request.displayName());
    if (request.role() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请选择账号角色");
    }
    normalizeLeaderId(request.role(), request.leaderId());
  }

  private void validateUpdate(AccountUpdateRequest request) {
    if (request == null || request.role() == null || request.isEnabled() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请填写姓名、角色和启用状态");
    }
    validateDisplayName(request.displayName());
    normalizeLeaderId(request.role(), request.leaderId());
  }

  private void validateDisplayName(String displayName) {
    if (blank(displayName) || displayName.trim().length() < 2 || displayName.trim().length() > 20) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "姓名长度需要 2-20 个字符");
    }
  }

  private Long normalizeLeaderId(Role role, Long leaderId) {
    if (role != Role.KEEPER) {
      return null;
    }
    if (leaderId == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "管家账号必须选择直属组长");
    }
    if (!repository.enabledLeaderExists(leaderId)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "直属组长不存在或已停用");
    }
    return leaderId;
  }

  private AccountAdminItem require(long id) {
    return repository.find(id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "账号不存在"));
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private Map<String, Object> accountDetail(AccountAdminItem item) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("id", item.id());
    detail.put("role", item.role().name());
    detail.put("leaderId", item.leaderId());
    detail.put("enabled", item.isEnabled());
    detail.put("permissions", item.permissions());
    detail.put("phoneLast4", item.phone() == null || item.phone().length() <= 4 ? item.phone() : item.phone().substring(item.phone().length() - 4));
    return detail;
  }

  private Set<String> normalizePermissions(Role role, Set<String> requested, Set<String> fallback) {
    Set<String> normalized = requested == null ? new LinkedHashSet<>(fallback) : new LinkedHashSet<>(requested);
    if (!PermissionCodes.ASSIGNABLE.containsAll(normalized)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "账号权限包含不支持的权限项");
    }
    if (role == Role.ADMIN) {
      normalized.add(PermissionCodes.TAG_MANAGEMENT);
    }
    return Set.copyOf(normalized);
  }

  private void audit(String action, AccountAdminItem item, Map<String, Object> detail) {
    try {
      auditLogger.log(action, AuthContext.username(), "account", String.valueOf(item.id()), objectMapper.writeValueAsString(detail));
    } catch (Exception ex) {
      auditLogger.log(action, AuthContext.username(), "account", String.valueOf(item.id()), "账号已变更");
    }
  }
}
