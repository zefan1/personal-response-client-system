package com.privateflow.modules.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
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
}
