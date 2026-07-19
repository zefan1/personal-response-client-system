package com.privateflow.modules.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.match.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Set<String> PUBLIC_POSTS = Set.of("/api/v1/auth/login", "/admin/api/v1/auth/login");
  private static final Set<String> ALLOWED_CORS_ORIGINS = Set.of(
      "http://localhost:5173",
      "http://127.0.0.1:5173",
      "http://localhost:5174",
      "http://127.0.0.1:5174");
  private final JwtService jwtService;
  private final AccountRepository accountRepository;
  private final AccountPermissionRepository permissionRepository;
  private final ObjectMapper objectMapper;

  @Autowired
  public JwtAuthenticationFilter(
      JwtService jwtService,
      AccountRepository accountRepository,
      AccountPermissionRepository permissionRepository,
      ObjectMapper objectMapper) {
    this.jwtService = jwtService;
    this.accountRepository = accountRepository;
    this.permissionRepository = permissionRepository;
    this.objectMapper = objectMapper;
  }

  JwtAuthenticationFilter(JwtService jwtService, AccountRepository accountRepository, ObjectMapper objectMapper) {
    this(jwtService, accountRepository, null, objectMapper);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    if ("GET".equalsIgnoreCase(request.getMethod()) && "/api/v1/auth/config".equals(path)) {
      return true;
    }
    return !path.startsWith("/api/v1/") && !path.startsWith("/admin/api/v1/")
        || ("POST".equalsIgnoreCase(request.getMethod())
            && (PUBLIC_POSTS.contains(path) || "/api/v1/auth/refresh".equals(path)));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    AuthUser user;
    try {
      String header = request.getHeader("Authorization");
      if (header == null || !header.startsWith("Bearer ")) {
        writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED, ApiErrorCodes.AUTH_FAILED, "请先登录");
        return;
      }
      user = jwtService.verify(header.substring("Bearer ".length()));
      Account account = accountRepository.findByPhone(user.username())
          .orElseThrow(() -> new ApiException(ApiErrorCodes.ACCOUNT_DISABLED, "账号已停用，请联系管理员"));
      if (!account.enabled()) {
        throw new ApiException(ApiErrorCodes.ACCOUNT_DISABLED, "账号已停用，请联系管理员");
      }
      if (user.tokenVersion() != account.tokenVersion()) {
        throw new ApiException(ApiErrorCodes.AUTH_FAILED, "账号权限或密码已变更，请重新登录");
      }
      user = new AuthUser(account.username(), account.displayName(), account.role(), account.leaderId(), account.tokenVersion());
    } catch (RuntimeException ex) {
      String code = ex instanceof ApiException apiEx ? apiEx.getErrorCode() : ApiErrorCodes.AUTH_FAILED;
      String message = ex instanceof ApiException apiEx ? apiEx.getMessage() : "登录状态无效，请重新登录";
      writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED, code, message);
      return;
    }
    if (request.getRequestURI().startsWith("/admin/api/v1/") && !allowedAdminAccess(request, user)) {
      writeError(request, response, HttpServletResponse.SC_FORBIDDEN, ApiErrorCodes.FORBIDDEN, "当前账号没有后台权限");
      return;
    }
    try {
      AuthContext.set(user);
      filterChain.doFilter(request, response);
    } finally {
      AuthContext.clear();
    }
  }

  private void writeError(HttpServletRequest request, HttpServletResponse response, int status, String code, String message) throws IOException {
    applyCorsHeaders(request, response);
    response.setStatus(status);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), ApiResponse.error(code, message));
  }

  private void applyCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
    String origin = request.getHeader("Origin");
    if (origin == null || !ALLOWED_CORS_ORIGINS.contains(origin)) {
      return;
    }
    response.setHeader("Access-Control-Allow-Origin", origin);
    response.setHeader("Vary", "Origin");
    response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
    response.setHeader("Access-Control-Allow-Headers", "Authorization,Content-Type");
  }

  private boolean allowedAdminAccess(HttpServletRequest request, AuthUser user) {
    if (user.role() == Role.ADMIN) {
      return true;
    }
    return request.getRequestURI().startsWith("/admin/api/v1/tags/")
        && permissionRepository != null
        && permissionRepository.hasPermission(user.username(), user.role(), PermissionCodes.TAG_MANAGEMENT);
  }
}
