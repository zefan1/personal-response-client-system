package com.privateflow.modules.api.ws;

import com.privateflow.modules.api.Role;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class DesktopWebSocketHandler extends TextWebSocketHandler {

  private final WsPushService pushService;

  public DesktopWebSocketHandler(WsPushService pushService) {
    this.pushService = pushService;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    String username = session.getAttributes().get("username").toString();
    Role role = (Role) session.getAttributes().get("role");
    Long leaderId = (Long) session.getAttributes().get("leaderId");
    long lastMessageId = (Long) session.getAttributes().get("lastMessageId");
    pushService.register(new WsSessionContext(username, role, leaderId, session, lastMessageId));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    if (message.getPayload().contains("\"PING\"")) {
      pushService.heartbeat(session);
      session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    pushService.remove(session);
  }
}
