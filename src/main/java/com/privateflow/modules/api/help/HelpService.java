package com.privateflow.modules.api.help;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.Account;
import com.privateflow.modules.api.auth.AccountRepository;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import java.time.Instant;
import java.util.List;
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
    String clientMessage = payload == null ? null : payload.effectiveClientMessage();
    if (requester == null || payload == null || blank(payload.phone()) || blank(clientMessage)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "phone and clientMessage are required");
    }
    if (requester.leaderId() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "leader is not configured");
    }
    Account leader = accountRepository.findById(requester.leaderId())
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "leader is not configured"));
    Account target = chooseOnlineTarget(leader);
    long helpId = ids.incrementAndGet();
    requesterByHelpId.put(helpId, requester.username());
    boolean noFallback = target == null;
    Account actualTarget = noFallback ? leader : target;
    wsPushService.pushWsMessage(actualTarget.username(), WsMessage.unsaved(noFallback ? "HELP_OFFLINE_REPLAY" : "HELP_REQUEST", Map.of(
        "helpId", helpId,
        "requesterName", requester.displayName(),
        "phone", nvl(payload.phone()),
        "clientMessage", clientMessage,
        "aiSuggestions", payload.aiSuggestions() == null ? List.of() : payload.aiSuggestions(),
        "keeperNote", nvl(payload.keeperNote()),
        "context", payload.context() == null ? Map.of() : payload.context(),
        "forwardedFrom", actualTarget.id().equals(leader.id()) ? Map.of() : Map.of("originalLeaderId", leader.id(), "originalLeaderName", leader.displayName()),
        "requestedAt", Instant.now().toString())));
    auditLogger.log("ASK_FOR_HELP", requester.username(), "HELP", String.valueOf(helpId), clientMessage);
    return Map.of(
        "helpId", helpId,
        "requestId", helpId,
        "leaderOnline", !noFallback,
        "targetLeaderName", actualTarget.displayName(),
        "forwarded", !actualTarget.id().equals(leader.id()),
        "noFallbackAvailable", noFallback);
  }

  public Map<String, Object> resolve(HelpResolvePayload payload) {
    AuthUser replier = AuthContext.current();
    Long helpId = payload == null ? null : payload.effectiveHelpId();
    List<HelpReplyPayload> replies = normalizeReplies(payload);
    if (replier == null || payload == null || helpId == null || replies.isEmpty()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "helpId and helperReplies are required");
    }
    String requester = requesterByHelpId.get(helpId);
    if (requester == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "help request not found");
    }
    wsPushService.pushWsMessage(requester, WsMessage.unsaved("HELP_RESPONSE", Map.of(
        "helpId", helpId,
        "helperReplies", replies,
        "helperName", replier.displayName(),
        "resolvedAt", Instant.now().toString())));
    auditLogger.log("RESOLVE_HELP", replier.username(), "HELP", String.valueOf(helpId), "resolved");
    return Map.of("success", true, "resolved", true);
  }

  private Account chooseOnlineTarget(Account directLeader) {
    if (wsPushService.isOnline(directLeader.username())) {
      return directLeader;
    }
    return accountRepository.findEnabledByRole(Role.LEADER).stream()
        .filter(account -> !account.id().equals(directLeader.id()))
        .filter(account -> wsPushService.isOnline(account.username()))
        .findFirst()
        .or(() -> accountRepository.findEnabledByRole(Role.ADMIN).stream()
            .filter(account -> wsPushService.isOnline(account.username()))
            .findFirst())
        .orElse(null);
  }

  private List<HelpReplyPayload> normalizeReplies(HelpResolvePayload payload) {
    if (payload == null) {
      return List.of();
    }
    if (payload.helperReplies() != null && !payload.helperReplies().isEmpty()) {
      return payload.helperReplies().stream()
          .filter(reply -> reply != null && !blank(reply.text()))
          .limit(3)
          .toList();
    }
    if (!blank(payload.replyText())) {
      return List.of(new HelpReplyPayload(payload.replyText(), "组长确认", "CONFIRMED"));
    }
    return List.of();
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String nvl(String value) {
    return value == null ? "" : value;
  }
}
