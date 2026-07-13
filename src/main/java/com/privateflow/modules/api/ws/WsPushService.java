package com.privateflow.modules.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.config.SystemConfigProvider;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Service
public class WsPushService {

  private static final Logger log = LoggerFactory.getLogger(WsPushService.class);
  private final Map<String, WsSessionContext> sessions = new ConcurrentHashMap<>();
  private final Map<String, ArrayDeque<WsMessage>> memoryQueue = new ConcurrentHashMap<>();
  private final WsOfflineMessageRepository offlineRepository;
  private final SystemConfigProvider configProvider;
  private final ObjectMapper objectMapper;
  private final Executor wsBroadcastExecutor;

  public WsPushService(
      WsOfflineMessageRepository offlineRepository,
      SystemConfigProvider configProvider,
      ObjectMapper objectMapper,
      @Qualifier("wsBroadcastExecutor") Executor wsBroadcastExecutor) {
    this.offlineRepository = offlineRepository;
    this.configProvider = configProvider;
    this.objectMapper = objectMapper;
    this.wsBroadcastExecutor = wsBroadcastExecutor;
  }

  public void register(WsSessionContext context) {
    WsSessionContext previous = sessions.put(context.username(), context);
    if (previous != null && previous.session().isOpen()) {
      closeQuietly(previous.session());
    }
    replay(context.username(), context.lastMessageId());
  }

  public void remove(WebSocketSession session) {
    sessions.entrySet().removeIf(entry -> entry.getValue().session().getId().equals(session.getId()));
  }

  public void heartbeat(WebSocketSession session) {
    sessions.values().stream()
        .filter(context -> context.session().getId().equals(session.getId()))
        .findFirst()
        .ifPresent(WsSessionContext::heartbeat);
  }

  public boolean isOnline(String username) {
    WsSessionContext context = sessions.get(username);
    return context != null && context.session().isOpen();
  }

  public void pushWsMessage(String username, WsMessage message) {
    WsMessage saved = ensureId(message);
    WsSessionContext context = sessions.get(username);
    if (context != null && context.session().isOpen()) {
      send(context, saved);
      offlineRepository.markDelivered(username, saved.messageId());
      return;
    }
    offlineRepository.save(username, saved);
    offerMemory(username, saved);
  }

  public void broadcastWs(WsMessage message) {
    sessions.keySet().forEach(username ->
        wsBroadcastExecutor.execute(() -> pushWsMessage(username, WsMessage.unsaved(message.type(), message.payload()))));
  }

  public void invalidateActiveSession(String username, String message) {
    WsSessionContext context = sessions.remove(username);
    if (context == null || !context.session().isOpen()) {
      return;
    }
    try {
      context.session().sendMessage(new TextMessage(objectMapper.writeValueAsString(
          WsMessage.unsaved("AUTH_INVALIDATED", Map.of(
              "message", message == null || message.isBlank() ? "登录状态已失效，请重新登录" : message)))));
    } catch (IOException ex) {
      log.debug("ignore session invalidation send failure username={}", username, ex);
    } finally {
      closeQuietly(context.session());
    }
  }

  public void replay(String username, long afterMessageId) {
    int limit = configProvider.get().wsReplayQueueSize();
    offlineRepository.replay(username, afterMessageId, limit).forEach(message -> {
      WsSessionContext context = sessions.get(username);
      if (context != null && context.session().isOpen()) {
        send(context, message);
        offlineRepository.markDelivered(username, message.messageId());
      }
    });
  }

  @Scheduled(fixedDelay = 10000)
  public void closeStaleSessions() {
    long timeout = configProvider.get().wsTimeoutS();
    sessions.values().forEach(context -> {
      if (Duration.between(context.lastHeartbeat(), Instant.now()).toSeconds() > timeout) {
        closeQuietly(context.session());
      }
    });
  }

  @Scheduled(cron = "0 30 3 * * *")
  public void cleanupOfflineQueue() {
    offlineRepository.cleanup(configProvider.get().wsOfflineRetentionDays());
  }

  @PreDestroy
  public void shutdown() {
    sessions.values().forEach(context -> closeQuietly(context.session()));
    sessions.clear();
    memoryQueue.clear();
  }

  private WsMessage ensureId(WsMessage message) {
    if (message.messageId() != null) {
      return message;
    }
    return new WsMessage(offlineRepository.nextMessageId(), message.type(), message.payload());
  }

  private void send(WsSessionContext context, WsMessage message) {
    try {
      context.session().sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
      context.markSent(message.messageId());
    } catch (IOException ex) {
      log.warn("WS send failed, fallback to offline queue username={}", context.username());
      offlineRepository.save(context.username(), message);
      offerMemory(context.username(), message);
      closeQuietly(context.session());
    }
  }

  private void offerMemory(String username, WsMessage message) {
    ArrayDeque<WsMessage> queue = memoryQueue.computeIfAbsent(username, ignored -> new ArrayDeque<>());
    synchronized (queue) {
      queue.offerLast(message);
      while (queue.size() > configProvider.get().wsReplayQueueSize()) {
        queue.pollFirst();
      }
    }
  }

  private void closeQuietly(WebSocketSession session) {
    try {
      session.close();
    } catch (IOException ex) {
      log.debug("ignore WS close failure", ex);
    }
  }
}
