package com.privateflow.modules.profile.service;

import com.privateflow.common.events.RecognizedConversationEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.profile.infra.ProfileUpdateFailureRecord;
import com.privateflow.modules.profile.infra.ProfileUpdateFailureRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class ProfileUpdateFailureService {

  private final ProfileUpdateFailureRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final AuditLogger auditLogger;

  public ProfileUpdateFailureService(
      ProfileUpdateFailureRepository repository,
      ApplicationEventPublisher eventPublisher,
      AuditLogger auditLogger) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.auditLogger = auditLogger;
  }

  public Map<String, Object> list(int limit) {
    requireAdmin();
    List<ProfileUpdateFailureRecord> items = repository.list(limit);
    return Map.of("items", items, "total", items.size());
  }

  public Map<String, Object> retry(long id) {
    requireAdmin();
    ProfileUpdateFailureRecord failure = repository.find(id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "profile update failure not found"));
    if (!repository.markRetrying(id)) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "profile update failure is not retryable");
    }
    eventPublisher.publishEvent(new RecognizedConversationEvent(
        failure.customerId(), failure.phone(), failure.rawMessages(), failure.operator(), failure.id()));
    auditLogger.log("PROFILE_UPDATE_RETRY", AuthContext.username(), "profile_update_failure", String.valueOf(id),
        "profile update retry queued");
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("accepted", true);
    result.put("failureId", id);
    result.put("retryCount", failure.retryCount() + 1);
    result.put("message", "档案更新已重新排队");
    return result;
  }

  private void requireAdmin() {
    if (AuthContext.current() == null || AuthContext.current().role() != Role.ADMIN) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "admin role required");
    }
  }
}
