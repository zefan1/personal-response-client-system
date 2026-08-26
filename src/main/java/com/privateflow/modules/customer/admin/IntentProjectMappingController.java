package com.privateflow.modules.customer.admin;

import com.privateflow.modules.match.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IntentProjectMappingController {

  private final IntentProjectMappingService service;

  public IntentProjectMappingController(IntentProjectMappingService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/v1/intent-project-mappings")
  public ApiResponse<Map<String, Object>> list(
      @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
    return ApiResponse.ok(service.current(refresh));
  }

  @PostMapping("/admin/api/v1/intent-project-mappings/refresh")
  public ApiResponse<Map<String, Object>> refresh() {
    return ApiResponse.ok(service.refreshOptions());
  }

  @PutMapping("/admin/api/v1/intent-project-mappings/{optionId}")
  public ApiResponse<IntentProjectMappingRule> save(
      @PathVariable("optionId") String optionId,
      @RequestBody IntentProjectMappingSaveRequest request) {
    return ApiResponse.ok(service.save(optionId, request));
  }

  @PostMapping("/admin/api/v1/intent-project-mappings/recompute")
  public ApiResponse<Map<String, Object>> recompute(
      @RequestParam(value = "onlyEmpty", defaultValue = "true") boolean onlyEmpty) {
    return ApiResponse.ok(service.recompute(onlyEmpty));
  }
}
