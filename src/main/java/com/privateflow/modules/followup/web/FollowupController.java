package com.privateflow.modules.followup.web;

import com.privateflow.modules.followup.ActionType;
import com.privateflow.modules.followup.FollowupErrorCodes;
import com.privateflow.modules.followup.FollowupException;
import com.privateflow.modules.followup.FollowupRule;
import com.privateflow.modules.followup.FollowupTodayResponse;
import com.privateflow.modules.followup.RulePage;
import com.privateflow.modules.followup.RuleRequest;
import com.privateflow.modules.followup.RuleSearchCriteria;
import com.privateflow.modules.followup.service.FollowupTodayService;
import com.privateflow.modules.followup.service.RuleAdminService;
import com.privateflow.modules.match.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FollowupController {

  private final FollowupTodayService todayService;
  private final RuleAdminService ruleAdminService;

  public FollowupController(FollowupTodayService todayService, RuleAdminService ruleAdminService) {
    this.todayService = todayService;
    this.ruleAdminService = ruleAdminService;
  }

  @GetMapping("/api/v1/followups/today")
  public ApiResponse<FollowupTodayResponse> today(@RequestParam(value = "keeperId", required = false) String keeperId) {
    return ApiResponse.ok(todayService.today(keeperId));
  }

  @GetMapping("/admin/api/v1/rules")
  public ApiResponse<RulePage> rules(
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "actionType", required = false) ActionType actionType,
      @RequestParam(value = "enabled", required = false) Boolean enabled) {
    return ApiResponse.ok(ruleAdminService.search(new RuleSearchCriteria(page, size, keyword, actionType, enabled)));
  }

  @PostMapping("/admin/api/v1/rules")
  public ApiResponse<FollowupRule> create(@RequestBody RuleRequest request) {
    return ApiResponse.ok(ruleAdminService.create(request));
  }

  @PutMapping("/admin/api/v1/rules/{id}")
  public ApiResponse<FollowupRule> update(@PathVariable("id") long id, @RequestBody RuleRequest request) {
    return ApiResponse.ok(ruleAdminService.update(id, request));
  }

  @DeleteMapping("/admin/api/v1/rules/{id}")
  public ApiResponse<Void> delete(@PathVariable("id") long id) {
    ruleAdminService.delete(id);
    return ApiResponse.ok(null);
  }

  @PutMapping("/admin/api/v1/rules/{id}/toggle")
  public ApiResponse<FollowupRule> toggle(@PathVariable("id") long id, @RequestBody ToggleRequest request) {
    return ApiResponse.ok(ruleAdminService.toggle(id, request.enabled()));
  }

  @ExceptionHandler(FollowupException.class)
  public ResponseEntity<ApiResponse<Void>> handleFollowup(FollowupException ex) {
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    if (FollowupErrorCodes.BAD_REQUEST.equals(ex.getErrorCode())
        || FollowupErrorCodes.CONDITION_PARSE_FAILED.equals(ex.getErrorCode())) {
      status = HttpStatus.BAD_REQUEST;
    } else if (FollowupErrorCodes.FORBIDDEN.equals(ex.getErrorCode())) {
      status = HttpStatus.FORBIDDEN;
    }
    return ResponseEntity.status(status).body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
  }

  public record ToggleRequest(boolean enabled) {
  }
}
