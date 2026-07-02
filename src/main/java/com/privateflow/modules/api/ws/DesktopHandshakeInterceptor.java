package com.privateflow.modules.api.ws;

import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.auth.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class DesktopHandshakeInterceptor implements HandshakeInterceptor {

  private final JwtService jwtService;

  public DesktopHandshakeInterceptor(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    if (!(request instanceof ServletServerHttpRequest servletRequest)) {
      return false;
    }
    HttpServletRequest http = servletRequest.getServletRequest();
    String token = http.getParameter("token");
    AuthUser user = jwtService.verify(token);
    attributes.put("username", user.username());
    attributes.put("role", user.role());
    attributes.put("leaderId", user.leaderId());
    attributes.put("lastMessageId", parseLong(http.getParameter("lastMessageId")));
    return true;
  }

  @Override
  public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
  }

  private long parseLong(String raw) {
    try {
      return raw == null || raw.isBlank() ? 0L : Long.parseLong(raw);
    } catch (NumberFormatException ex) {
      return 0L;
    }
  }
}
