package com.privateflow.modules.api.ws;

import com.privateflow.modules.api.Role;
import java.time.Instant;
import org.springframework.web.socket.WebSocketSession;

public class WsSessionContext {

  private final String username;
  private final Role role;
  private final Long leaderId;
  private final WebSocketSession session;
  private final Instant connectedAt;
  private volatile Instant lastHeartbeat;
  private volatile long lastMessageId;

  public WsSessionContext(String username, Role role, Long leaderId, WebSocketSession session, long lastMessageId) {
    this.username = username;
    this.role = role;
    this.leaderId = leaderId;
    this.session = session;
    this.connectedAt = Instant.now();
    this.lastHeartbeat = Instant.now();
    this.lastMessageId = lastMessageId;
  }

  public String username() { return username; }
  public Role role() { return role; }
  public Long leaderId() { return leaderId; }
  public WebSocketSession session() { return session; }
  public Instant connectedAt() { return connectedAt; }
  public Instant lastHeartbeat() { return lastHeartbeat; }
  public long lastMessageId() { return lastMessageId; }
  public void heartbeat() { this.lastHeartbeat = Instant.now(); }
  public void markSent(long messageId) { this.lastMessageId = Math.max(this.lastMessageId, messageId); }
}
