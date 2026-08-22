package com.privateflow.modules.api.chat;

import com.privateflow.modules.match.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PendingSendConfirmationAdminController {

  private final PendingSendConfirmationAdminService service;

  public PendingSendConfirmationAdminController(PendingSendConfirmationAdminService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/v1/reply-confirmations/summary")
  public ApiResponse<Map<String, Object>> summary(
      @RequestParam(value = "days", defaultValue = "7") int days,
      @RequestParam(value = "operator", required = false) String operator) {
    return ApiResponse.ok(service.summary(days, operator));
  }
}
