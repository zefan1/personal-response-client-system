package com.privateflow.modules.desktop;

import com.privateflow.modules.match.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DesktopStatusController {

  private final DesktopStatusService desktopStatusService;

  public DesktopStatusController(DesktopStatusService desktopStatusService) {
    this.desktopStatusService = desktopStatusService;
  }

  @GetMapping("/api/v1/desktop/status")
  public ApiResponse<DesktopStatusResponse> status() {
    return ApiResponse.ok(desktopStatusService.currentStatus());
  }
}
