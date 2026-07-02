package com.privateflow.modules.api.help;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.Account;
import com.privateflow.modules.api.auth.AccountRepository;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class HelpService {

  private final AtomicLong ids = new AtomicLong(System.currentTimeMillis());
  private final Map<Long, String> requesterByHelpId = new ConcurrentHashMap<>();
  private final AccountRepository accountRepository;
  private final WsPushService wsPushService;
  private final AuditLogger auditLogger;

  public HelpService(AccountRepository accountRepository, WsPushService wsPushService, AuditLogger auditLogger) {
    this.accountRepository = accountRepository;
    this.wsPushService = wsPushService;
    this.auditLogger = auditLogger;
  }

  public Map<String, Object> request(HelpRequestPayload payload) {
    AuthUser requester = AuthContext.current();
    if (requester == null || payload == null || blank(payload.question())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "question is required");
    }
    if (requester.leaderId() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "leader is not configured");
    }
    Account leader = accountRepository.findById(requester.leaderId())
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "leader is not configured"));
    long helpId = ids.incrementAndGet();
    requesterByHelpId.put(helpId, requester.username());
    wsPushService.pushWsMessage(leader.username(), WsMessage.unsaved("HELP_REQUEST", Map.of(
        "helpId", helpId,
        "requesterName", requester.displayName(),
        "phone", nvl(payload.phone()),
        "clientMessage", payload.question(),
        "context", payload.context() == null ? Map.of() : payload.context(),
        "requestedAt", Instant.now().toString())));
    auditLogger.log("ASK_FOR_HELP", requester.username(), "HELP", String.valueOf(helpId), payload.question());
    return Map.of("requestId", helpId, "forwardedTo", leader.username());
  }

  public Map<String, Object> resolve(HelpResolvePayload payload) {
    AuthUser replier = AuthContext.current();
    if (replier == null || payload == null || payload.requestId() == null || blank(payload.replyText())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "requestId and replyText are required");
    }
    String requester = requesterByHelpId.get(payload.requestId());
    if (requester == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "help request not found");
    }
    wsPushService.pushWsMessage(requester, WsMessage.unsaved("HELP_RESPONSE", Map.of(
        "helpId", payload.requestId(),
        "helperReplies", java.util.List.of(Map.of("text", payload.replyText(), "direction", "HELP_REPLY", "source", "CONFIRMED")),
        "helperName", replier.displayName(),
        "resolvedAt", Instant.now().toString())));
    auditLogger.log("RESOLVE_HELP", replier.username(), "HELP", String.valueOf(payload.requestId()), "resolved");
    return Map.of("resolved", true);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String nvl(String value) {
    return value == null ? "" : value;
  }
}
