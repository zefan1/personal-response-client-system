package com.privateflow.modules.templates;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PersonalTemplateService {

  private static final int TITLE_MAX_LENGTH = 120;
  private static final int BODY_MAX_LENGTH = 4000;
  private static final int OPTIONAL_METADATA_MAX_LENGTH = 100;
  private static final int SESSION_ID_MAX_LENGTH = 80;
  private static final int MAX_LABELS = 20;
  private static final int LABEL_MAX_LENGTH = 80;

  private final PersonalTemplateRepository repository;

  public PersonalTemplateService(PersonalTemplateRepository repository) {
    this.repository = repository;
  }

  public PersonalTemplate save(PersonalTemplateRequest request) {
    AuthUser user = requireAuthenticatedUser();
    NormalizedRequest normalized = normalize(request);
    long templateId = repository.insertPersonal(
        user.username(),
        normalized.title(),
        normalized.body(),
        normalized.metadata(),
        normalized.sourceReplySessionId());
    repository.insertCandidate(
        templateId,
        user.username(),
        normalized.originalAiReply(),
        normalized.title(),
        normalized.body(),
        normalized.metadata());
    return repository.findPersonal(templateId, user.username())
        .orElseThrow(() -> new ApiException(ApiErrorCodes.INTERNAL_ERROR, "saved template is unavailable"));
  }

  public List<PersonalTemplate> listMine() {
    return repository.findMine(requireAuthenticatedUser().username());
  }

  public List<TeamTemplate> listTeamTemplates() {
    requireAuthenticatedUser();
    return repository.findPublishedTeamTemplates();
  }

  public Map<String, Object> recordPersonalTemplateUse(long templateId) {
    AuthUser user = requireAuthenticatedUser();
    if (!repository.incrementPersonalUsage(templateId, user.username())) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "personal template is unavailable");
    }
    return Map.of("recorded", true, "source", "PERSONAL");
  }

  public Map<String, Object> recordTeamTemplateUse(long quickSearchItemId) {
    requireAuthenticatedUser();
    if (!repository.incrementTeamUsage(quickSearchItemId)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "team template is unavailable");
    }
    return Map.of("recorded", true, "source", "TEAM");
  }

  private AuthUser requireAuthenticatedUser() {
    AuthUser user = AuthContext.current();
    if (user == null || user.username() == null || user.username().isBlank()) {
      throw new ApiException(ApiErrorCodes.AUTH_FAILED, "authentication is required");
    }
    return user;
  }

  private NormalizedRequest normalize(PersonalTemplateRequest request) {
    if (request == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "template is required");
    }
    String title = required("title", request.title(), TITLE_MAX_LENGTH);
    String body = required("body", request.body(), BODY_MAX_LENGTH);
    String originalAiReply = optional("originalAiReply", request.originalAiReply(), BODY_MAX_LENGTH);
    TemplateMetadata metadata = normalizeMetadata(request.metadata());
    return new NormalizedRequest(
        title,
        body,
        originalAiReply == null ? body : originalAiReply,
        metadata,
        optional("sourceReplySessionId", request.sourceReplySessionId(), SESSION_ID_MAX_LENGTH));
  }

  private TemplateMetadata normalizeMetadata(TemplateMetadata metadata) {
    TemplateMetadata value = metadata == null ? new TemplateMetadata(null, null, null, List.of()) : metadata;
    List<String> labels = value.labels() == null ? List.of() : value.labels();
    if (labels.size() > MAX_LABELS) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "labels exceeds " + MAX_LABELS + " items");
    }
    LinkedHashSet<String> normalizedLabels = new LinkedHashSet<>();
    for (String label : labels) {
      String normalized = required("labels", label, LABEL_MAX_LENGTH);
      normalizedLabels.add(normalized);
    }
    return new TemplateMetadata(
        optional("metadata.channelCode", value.channelCode(), OPTIONAL_METADATA_MAX_LENGTH),
        optional("metadata.scene", value.scene(), OPTIONAL_METADATA_MAX_LENGTH),
        optional("metadata.leadType", value.leadType(), OPTIONAL_METADATA_MAX_LENGTH),
        new ArrayList<>(normalizedLabels));
  }

  private String required(String field, String value, int maxLength) {
    String normalized = optional(field, value, maxLength);
    if (normalized == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, field + " is required");
    }
    return normalized;
  }

  private String optional(String field, String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, field + " exceeds " + maxLength + " characters");
    }
    return normalized;
  }

  private record NormalizedRequest(
      String title,
      String body,
      String originalAiReply,
      TemplateMetadata metadata,
      String sourceReplySessionId
  ) {
  }
}
