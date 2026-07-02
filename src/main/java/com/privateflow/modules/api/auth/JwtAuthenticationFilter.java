package com.privateflow.modules.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.match.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Set<String> PUBLIC_POSTS = Set.of("/api/v1/auth/login", "/admin/api/v1/auth/login");
  private final JwtService jwtService;
  private final AccountRepository accountRepository;
  private final ObjectMapper objectMapper;

  public JwtAuthenticationFilter(JwtService jwtService, AccountRepository accountRepository, ObjectMapper objectMapper) {
    this.jwtService = jwtService;
    this.accountRepository = accountRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    if ("GET".equalsIgnoreCase(request.getMethod()) && "/api/v1/auth/config".equals(path)) {
      return true;
    }
    return !path.startsWith("/api/v1/") && !path.startsWith("/admin/api/v1/")
        || ("POST".equalsIgnoreCase(request.getMethod()) && PUBLIC_POSTS.contains(path));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    AuthUser user;
    try {
      String header = request.getHeader("Authorization");
      if (header == null || !header.startsWith("Bearer ")) {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ApiErrorCodes.AUTH_FAILED, "Token required");
        return;
      }
      user = jwtService.verify(header.substring("Bearer ".length()));
      Account account = accountRepository.findByPhone(user.username())
          .orElseThrow(() -> new ApiException(ApiErrorCodes.ACCOUNT_DISABLED, "account disabled"));
      if (!account.enabled()) {
        throw new ApiException(ApiErrorCodes.ACCOUNT_DISABLED, "account disabled");
      }
      user = new AuthUser(account.username(), account.displayName(), account.role(), account.leaderId());
    } catch (RuntimeException ex) {
      String code = ex instanceof ApiException apiEx ? apiEx.getErrorCode() : ApiErrorCodes.AUTH_FAILED;
      writeError(response, HttpServletResponse.SC_UNAUTHORIZED, code, "Token invalid or expired");
      return;
    }
    if (request.getRequestURI().startsWith("/admin/api/v1/")
        && user.role() == com.privateflow.modules.api.Role.KEEPER
        && !keeperAnalyticsOverview(request)) {
      writeError(response, HttpServletResponse.SC_FORBIDDEN, ApiErrorCodes.FORBIDDEN, "permission denied");
      return;
    }
    try {
      AuthContext.set(user);
      filterChain.doFilter(request, response);
    } finally {
      AuthContext.clear();
    }
  }

  private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), ApiResponse.error(code, message));
  }

  private boolean keeperAnalyticsOverview(HttpServletRequest request) {
    return "GET".equalsIgnoreCase(request.getMethod())
        && "/admin/api/v1/analytics/overview".equals(request.getRequestURI());
  }
}
