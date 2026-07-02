package com.privateflow.modules.api.account;

import com.privateflow.modules.api.Role;
import com.privateflow.modules.match.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountAdminController {

  private final AccountAdminService service;

  public AccountAdminController(AccountAdminService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/v1/accounts")
  public ApiResponse<Map<String, Object>> list(
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "page_size", defaultValue = "20") int pageSize,
      @RequestParam(value = "role", required = false) Role role,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "is_enabled", required = false) Integer enabled) {
    Boolean enabledFlag = enabled == null ? null : enabled == 1;
    return ApiResponse.ok(service.list(page, pageSize, role, keyword, enabledFlag));
  }

  @PostMapping("/admin/api/v1/accounts")
  public ApiResponse<AccountAdminItem> create(@RequestBody AccountCreateRequest request) {
    return ApiResponse.ok(service.create(request));
  }

  @PutMapping("/admin/api/v1/accounts/{id}")
  public ApiResponse<AccountAdminItem> update(@PathVariable("id") long id, @RequestBody AccountUpdateRequest request) {
    return ApiResponse.ok(service.update(id, request));
  }

  @PutMapping("/admin/api/v1/accounts/{id}/toggle")
  public ApiResponse<AccountAdminItem> toggle(@PathVariable("id") long id, @RequestBody AccountToggleRequest request) {
    return ApiResponse.ok(service.toggle(id, request != null && Boolean.TRUE.equals(request.isEnabled())));
  }

  @PutMapping("/admin/api/v1/accounts/{id}/reset-password")
  public ApiResponse<Map<String, Object>> resetPassword(@PathVariable("id") long id, @RequestBody PasswordResetRequest request) {
    return ApiResponse.ok(service.resetPassword(id, request));
  }

  @DeleteMapping("/admin/api/v1/accounts/{id}")
  public ApiResponse<Void> delete(@PathVariable("id") long id) {
    service.delete(id);
    return ApiResponse.ok(null);
  }
}
