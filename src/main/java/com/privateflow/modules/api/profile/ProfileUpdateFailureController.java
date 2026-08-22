package com.privateflow.modules.api.profile;

import com.privateflow.modules.match.ApiResponse;
import com.privateflow.modules.profile.service.ProfileUpdateFailureService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileUpdateFailureController {

  private final ProfileUpdateFailureService service;

  public ProfileUpdateFailureController(ProfileUpdateFailureService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/v1/profile-update-failures")
  public ApiResponse<Map<String, Object>> list(
      @RequestParam(value = "limit", defaultValue = "50") int limit) {
    return ApiResponse.ok(service.list(limit));
  }

  @PostMapping("/admin/api/v1/profile-update-failures/{id}/retry")
  public ApiResponse<Map<String, Object>> retry(@PathVariable("id") long id) {
    return ApiResponse.ok(service.retry(id));
  }
}
