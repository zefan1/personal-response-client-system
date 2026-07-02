package com.privateflow.modules.api.account;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.auth.RefreshTokenStore;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

@Service
public class AccountAdminService {

  private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
  private final AccountAdminRepository repository;
  private final RefreshTokenStore refreshTokenStore;

  public AccountAdminService(AccountAdminRepository repository, RefreshTokenStore refreshTokenStore) {
    this.repository = repository;
    this.refreshTokenStore = refreshTokenStore;
  }

  public Map<String, Object> list(int page, int pageSize, Role role, String keyword, Boolean enabled) {
    int safePage = Math.max(1, page);
    int safePageSize = Math.max(10, Math.min(50, pageSize));
    return Map.of(
        "total", repository.count(role, keyword, enabled),
        "page", safePage,
        "pageSize", safePageSize,
        "list", repository.list(safePage, safePageSize, role, keyword, enabled));
  }

  public AccountAdminItem create(AccountCreateRequest request) {
    validateCreate(request);
    long id = repository.create(new AccountCreateRequest(
        request.phone().trim(),
        request.password(),
        request.displayName().trim(),
        request.role(),
        normalizeLeaderId(request.role(), request.leaderId())),
        BCrypt.hashpw(request.password(), BCrypt.gensalt()));
    return require(id);
  }

  public AccountAdminItem update(long id, AccountUpdateRequest request) {
    AccountAdminItem current = require(id);
    validateUpdate(request);
    Long leaderId = normalizeLeaderId(request.role(), request.leaderId());
    AuthUser operator = AuthContext.current();
    if (operator.username().equals(current.phone())
        && (current.role() != request.role() || current.isEnabled() != Boolean.TRUE.equals(request.isEnabled()))) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "cannot modify own role or status");
    }
    repository.update(id, request, leaderId);
    if (!Boolean.TRUE.equals(request.isEnabled())) {
      refreshTokenStore.revoke(current.phone());
    }
    return require(id);
  }

  public AccountAdminItem toggle(long id, boolean enabled) {
    AccountAdminItem current = require(id);
    if (AuthContext.username().equals(current.phone())) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "cannot disable own account");
    }
    repository.toggle(id, enabled);
    if (!enabled) {
      refreshTokenStore.revoke(current.phone());
    }
    return require(id);
  }

  public Map<String, Object> resetPassword(long id, PasswordResetRequest request) {
    AccountAdminItem current = require(id);
    if (request == null || request.newPassword() == null || request.newPassword().length() < 6) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "password length must be at least 6");
    }
    repository.resetPassword(id, BCrypt.hashpw(request.newPassword(), BCrypt.gensalt()));
    refreshTokenStore.revoke(current.phone());
    return Map.of("id", id, "revokedRefreshToken", true);
  }

  public void delete(long id) {
    AccountAdminItem current = require(id);
    if (AuthContext.username().equals(current.phone())) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "cannot delete own account");
    }
    if (current.role() == Role.LEADER) {
      int keeperCount = repository.enabledKeeperCount(id);
      if (keeperCount > 0) {
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, "leader has " + keeperCount + " enabled keepers");
      }
    }
    repository.delete(id);
    refreshTokenStore.revoke(current.phone());
  }

  private void validateCreate(AccountCreateRequest request) {
    if (request == null || blank(request.phone()) || !PHONE.matcher(request.phone().trim()).matches()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "phone format invalid");
    }
    if (repository.phoneExists(request.phone().trim())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "phone already registered");
    }
    if (request.password() == null || request.password().length() < 6) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "password length must be at least 6");
    }
    validateDisplayName(request.displayName());
    if (request.role() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "role required");
    }
    normalizeLeaderId(request.role(), request.leaderId());
  }

  private void validateUpdate(AccountUpdateRequest request) {
    if (request == null || request.role() == null || request.isEnabled() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "displayName, role and isEnabled are required");
    }
    validateDisplayName(request.displayName());
    normalizeLeaderId(request.role(), request.leaderId());
  }

  private void validateDisplayName(String displayName) {
    if (blank(displayName) || displayName.trim().length() < 2 || displayName.trim().length() > 20) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "displayName length must be 2-20");
    }
  }

  private Long normalizeLeaderId(Role role, Long leaderId) {
    if (role != Role.KEEPER) {
      return null;
    }
    if (leaderId == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "keeper must specify leaderId");
    }
    if (!repository.enabledLeaderExists(leaderId)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "leader not found or disabled");
    }
    return leaderId;
  }

  private AccountAdminItem require(long id) {
    return repository.find(id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "account not found"));
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
