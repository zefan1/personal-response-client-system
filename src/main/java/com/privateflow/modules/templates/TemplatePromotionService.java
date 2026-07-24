package com.privateflow.modules.templates;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.quicksearch.ContentType;
import com.privateflow.modules.quicksearch.admin.QuickSearchAdminService;
import com.privateflow.modules.quicksearch.admin.QuickSearchItemRequest;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplatePromotionService {

  private final PersonalTemplateRepository repository;
  private final QuickSearchAdminService quickSearchAdminService;

  public TemplatePromotionService(
      PersonalTemplateRepository repository,
      QuickSearchAdminService quickSearchAdminService) {
    this.repository = repository;
    this.quickSearchAdminService = quickSearchAdminService;
  }

  public List<TemplatePromotionCandidate> listCandidates(TemplatePromotionCandidateStatus status) {
    requireAdministrator();
    return repository.findCandidates(status);
  }

  @Transactional
  public Map<String, Object> publish(long candidateId, PublishTeamTemplateRequest request) {
    AuthUser administrator = requireAdministrator();
    TemplatePromotionCandidate candidate = repository.findCandidateForUpdate(candidateId)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "template candidate not found"));
    requireCandidate(candidate);
    NormalizedPublishRequest normalized = normalize(request, candidate);
    long quickSearchItemId = quickSearchAdminService.createTeamTemplate(new QuickSearchItemRequest(
        ContentType.TEMPLATE,
        normalized.leadType(),
        normalized.title(),
        normalized.shortcutCode(),
        candidate.editedBody(),
        null,
        0,
        normalized.enabled(),
        null));
    repository.insertPublication(candidateId, quickSearchItemId, administrator.username());
    repository.markPublished(candidateId, administrator.username());
    quickSearchAdminService.broadcastTeamTemplateRefresh();
    return Map.of("candidateId", candidateId, "quickSearchItemId", quickSearchItemId);
  }

  @Transactional
  public void markNotPublished(long candidateId) {
    AuthUser administrator = requireAdministrator();
    TemplatePromotionCandidate candidate = repository.findCandidateForUpdate(candidateId)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "template candidate not found"));
    requireCandidate(candidate);
    repository.markNotPublished(candidateId, administrator.username());
  }

  private AuthUser requireAdministrator() {
    AuthUser user = AuthContext.current();
    if (user == null) {
      throw new ApiException(ApiErrorCodes.AUTH_FAILED, "authentication is required");
    }
    if (user.role() != Role.ADMIN) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "administrator permission is required");
    }
    return user;
  }

  private void requireCandidate(TemplatePromotionCandidate candidate) {
    if (candidate.status() != TemplatePromotionCandidateStatus.CANDIDATE) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "template candidate already decided");
    }
  }

  private NormalizedPublishRequest normalize(
      PublishTeamTemplateRequest request,
      TemplatePromotionCandidate candidate) {
    if (request == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "publish request is required");
    }
    String title = required("title", request.title(), 120);
    String shortcutCode = optional(request.shortcutCode(), 20);
    if (shortcutCode == null) {
      shortcutCode = "TM" + Long.toString(candidate.id(), 36).toUpperCase();
    }
    if (!shortcutCode.matches("[A-Za-z0-9]{2,20}")) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "shortcutCode must be 2-20 letters or digits");
    }
    String leadType = optional(request.leadType(), 32);
    if (leadType == null) {
      leadType = candidate.metadata().leadType();
    }
    return new NormalizedPublishRequest(title, shortcutCode, leadType, !Boolean.FALSE.equals(request.enabled()));
  }

  private String required(String field, String value, int maxLength) {
    String normalized = optional(value, maxLength);
    if (normalized == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, field + " is required");
    }
    return normalized;
  }

  private String optional(String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "value exceeds " + maxLength + " characters");
    }
    return normalized;
  }

  private record NormalizedPublishRequest(
      String title,
      String shortcutCode,
      String leadType,
      boolean enabled
  ) {
  }
}
