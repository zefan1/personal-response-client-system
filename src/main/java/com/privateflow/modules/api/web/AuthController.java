package com.privateflow.modules.api.web;

import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthService;
import com.privateflow.modules.api.auth.LoginRequest;
import com.privateflow.modules.api.auth.LoginResponse;
import com.privateflow.modules.api.auth.RefreshRequest;
import com.privateflow.modules.match.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/api/v1/auth/login")
  public ApiResponse<LoginResponse> desktopLogin(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    return ApiResponse.ok(authService.login(request, clientIp(httpRequest), false));
  }

  @PostMapping("/admin/api/v1/auth/login")
  public ApiResponse<LoginResponse> adminLogin(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    return ApiResponse.ok(authService.login(request, clientIp(httpRequest), true));
  }

  @PostMapping("/api/v1/auth/refresh")
  public ApiResponse<LoginResponse> refresh(@RequestBody RefreshRequest request) {
    return ApiResponse.ok(authService.refresh(request, AuthContext.current()));
  }

  private String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
