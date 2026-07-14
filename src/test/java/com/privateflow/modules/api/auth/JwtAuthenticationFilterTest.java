package com.privateflow.modules.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.Role;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtAuthenticationFilterTest {

  @Test
  void optionsPreflightBypassesTokenAuthentication() throws Exception {
    JwtService jwtService = mock(JwtService.class);
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, mock(AccountRepository.class), new ObjectMapper());
    MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/admin/api/v1/auth/login");
    request.addHeader("Origin", "http://localhost:5173");
    request.addHeader("Access-Control-Request-Method", "POST");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(jwtService, never()).verify(any());
  }

  @Test
  void authFailureIncludesCorsHeadersForLocalFrontend() throws Exception {
    JwtService jwtService = mock(JwtService.class);
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, mock(AccountRepository.class), new ObjectMapper());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/api/v1/accounts");
    request.addHeader("Origin", "http://127.0.0.1:5173");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://127.0.0.1:5173");
    assertThat(response.getHeader("Access-Control-Allow-Headers")).contains("Authorization");
    assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
    assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("\"errorCode\":\"80-10002\"");
    assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("请先登录");
    verify(chain, never()).doFilter(any(), any());
    verify(jwtService, never()).verify(any());
  }

  @Test
  void leaderCannotAccessAdminApis() throws Exception {
    JwtService jwtService = mock(JwtService.class);
    AccountRepository accountRepository = mock(AccountRepository.class);
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, accountRepository, new ObjectMapper());
    when(jwtService.verify("leader-token")).thenReturn(new AuthUser("18800000001", "组长", Role.LEADER, null));
    when(accountRepository.findByPhone("18800000001")).thenReturn(Optional.of(
        new Account(2L, "18800000001", "hash", "组长", Role.LEADER, null, true)));
    FilterChain chain = mock(FilterChain.class);

    MockHttpServletRequest getRequest = authed("GET", "/admin/api/v1/accounts", "leader-token");
    MockHttpServletResponse getResponse = new MockHttpServletResponse();
    filter.doFilter(getRequest, getResponse, chain);
    assertThat(getResponse.getStatus()).isEqualTo(403);

    MockHttpServletRequest postRequest = authed("POST", "/admin/api/v1/configs", "leader-token");
    MockHttpServletResponse postResponse = new MockHttpServletResponse();
    filter.doFilter(postRequest, postResponse, chain);

    assertThat(postResponse.getStatus()).isEqualTo(403);
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void explicitTagPermissionAllowsOnlyTagAdminEndpoints() throws Exception {
    JwtService jwtService = mock(JwtService.class);
    AccountRepository accountRepository = mock(AccountRepository.class);
    AccountPermissionRepository permissionRepository = mock(AccountPermissionRepository.class);
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
        jwtService,
        accountRepository,
        permissionRepository,
        new ObjectMapper());
    when(jwtService.verify("leader-token")).thenReturn(new AuthUser("18800000001", "组长", Role.LEADER, null));
    when(accountRepository.findByPhone("18800000001")).thenReturn(Optional.of(
        new Account(2L, "18800000001", "hash", "组长", Role.LEADER, null, true)));
    when(permissionRepository.hasPermission("18800000001", Role.LEADER, PermissionCodes.TAG_MANAGEMENT))
        .thenReturn(true);
    FilterChain chain = mock(FilterChain.class);

    MockHttpServletRequest tagRequest = authed("GET", "/admin/api/v1/tags/categories", "leader-token");
    MockHttpServletResponse tagResponse = new MockHttpServletResponse();
    filter.doFilter(tagRequest, tagResponse, chain);
    verify(chain).doFilter(tagRequest, tagResponse);

    MockHttpServletRequest accountRequest = authed("GET", "/admin/api/v1/accounts", "leader-token");
    MockHttpServletResponse accountResponse = new MockHttpServletResponse();
    filter.doFilter(accountRequest, accountResponse, chain);
    assertThat(accountResponse.getStatus()).isEqualTo(403);
  }

  @Test
  void adminCanWriteAndKeeperCannotAccessAdminApis() throws Exception {
    JwtService jwtService = mock(JwtService.class);
    AccountRepository accountRepository = mock(AccountRepository.class);
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, accountRepository, new ObjectMapper());
    when(jwtService.verify("admin-token")).thenReturn(new AuthUser("18800000000", "管理员", Role.ADMIN, null));
    when(jwtService.verify("keeper-token")).thenReturn(new AuthUser("18800000002", "管家", Role.KEEPER, 2L));
    when(accountRepository.findByPhone("18800000000")).thenReturn(Optional.of(
        new Account(1L, "18800000000", "hash", "管理员", Role.ADMIN, null, true)));
    when(accountRepository.findByPhone("18800000002")).thenReturn(Optional.of(
        new Account(3L, "18800000002", "hash", "管家", Role.KEEPER, 2L, true)));
    FilterChain chain = mock(FilterChain.class);

    MockHttpServletRequest adminPost = authed("POST", "/admin/api/v1/configs", "admin-token");
    MockHttpServletResponse adminResponse = new MockHttpServletResponse();
    filter.doFilter(adminPost, adminResponse, chain);
    verify(chain).doFilter(adminPost, adminResponse);

    MockHttpServletRequest keeperOverview = authed("GET", "/admin/api/v1/analytics/overview", "keeper-token");
    MockHttpServletResponse overviewResponse = new MockHttpServletResponse();
    filter.doFilter(keeperOverview, overviewResponse, chain);
    assertThat(overviewResponse.getStatus()).isEqualTo(403);

    MockHttpServletRequest keeperAccounts = authed("GET", "/admin/api/v1/accounts", "keeper-token");
    MockHttpServletResponse keeperResponse = new MockHttpServletResponse();
    filter.doFilter(keeperAccounts, keeperResponse, chain);

    assertThat(keeperResponse.getStatus()).isEqualTo(403);
  }

  private MockHttpServletRequest authed(String method, String path, String token) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.addHeader("Authorization", "Bearer " + token);
    return request;
  }
}
