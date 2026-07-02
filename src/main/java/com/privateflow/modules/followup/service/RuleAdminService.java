package com.privateflow.modules.followup.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.followup.ActionType;
import com.privateflow.modules.followup.FollowupErrorCodes;
import com.privateflow.modules.followup.FollowupException;
import com.privateflow.modules.followup.FollowupRule;
import com.privateflow.modules.followup.RulePage;
import com.privateflow.modules.followup.RuleRequest;
import com.privateflow.modules.followup.RuleSearchCriteria;
import com.privateflow.modules.followup.infra.FollowupRuleRepository;
import org.springframework.stereotype.Service;

@Service
public class RuleAdminService {

  private static final int MAX_AND_CONDITIONS = 8;
  private static final int MAX_OR_GROUPS = 2;
  private final FollowupRuleRepository ruleRepository;
  private final ObjectMapper objectMapper;
  private final RuleLoader ruleLoader;
  private final AuditLogger auditLogger;

  public RuleAdminService(
      FollowupRuleRepository ruleRepository,
      ObjectMapper objectMapper,
      RuleLoader ruleLoader,
      AuditLogger auditLogger) {
    this.ruleRepository = ruleRepository;
    this.objectMapper = objectMapper;
    this.ruleLoader = ruleLoader;
    this.auditLogger = auditLogger;
  }

  public RulePage search(RuleSearchCriteria criteria) {
    return ruleRepository.search(criteria);
  }

  public FollowupRule create(RuleRequest request) {
    validate(request, false, null);
    long id = ruleRepository.create(request);
    ruleLoader.refresh();
    auditLogger.log("CREATE_FOLLOWUP_RULE", AuthContext.username(), "followup_rules", String.valueOf(id), request.name());
    return ruleRepository.findById(id).orElseThrow();
  }

  public FollowupRule update(long id, RuleRequest request) {
    FollowupRule existing = require(id);
    validate(request, existing.builtin(), existing);
    ruleRepository.update(id, request, existing.builtin());
    ruleLoader.refresh();
    auditLogger.log("UPDATE_FOLLOWUP_RULE", AuthContext.username(), "followup_rules", String.valueOf(id), existing.name());
    return ruleRepository.findById(id).orElseThrow();
  }

  public void delete(long id) {
    FollowupRule existing = require(id);
    if (existing.builtin()) {
      throw new FollowupException(FollowupErrorCodes.FORBIDDEN, "builtin rule cannot be deleted");
    }
    ruleRepository.delete(id);
    ruleLoader.refresh();
    auditLogger.log("DELETE_FOLLOWUP_RULE", AuthContext.username(), "followup_rules", String.valueOf(id), existing.name());
  }

  public FollowupRule toggle(long id, boolean enabled) {
    FollowupRule existing = require(id);
    ruleRepository.toggle(id, enabled);
    ruleLoader.refresh();
    auditLogger.log("TOGGLE_FOLLOWUP_RULE", AuthContext.username(), "followup_rules", String.valueOf(id), existing.name() + " enabled=" + enabled);
    return ruleRepository.findById(id)
        .orElseThrow(() -> new FollowupException(FollowupErrorCodes.BAD_REQUEST, "rule not found"));
  }

  private void validate(RuleRequest request, boolean builtinUpdate, FollowupRule existing) {
    if (request == null) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "request required");
    }
    if (!builtinUpdate) {
      validateName(request.name(), existing);
    }
    if (builtinUpdate && existing != null && request.name() != null && !request.name().isBlank()
        && !existing.name().equals(request.name())) {
      throw new FollowupException(FollowupErrorCodes.FORBIDDEN, "builtin rule name cannot be changed");
    }
    if (builtinUpdate && existing != null && request.actionType() != null && existing.actionType() != request.actionType()) {
      throw new FollowupException(FollowupErrorCodes.FORBIDDEN, "builtin rule actionType cannot be changed");
    }
    if (request.conditionJson() == null || request.conditionJson().isBlank()) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "conditionJson required");
    }
    validateJson(request);
    if (!builtinUpdate && request.actionType() == null) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "actionType required");
    }
    if (!builtinUpdate && request.actionType() != ActionType.ALERT
        && request.actionType() != ActionType.TAG_CHANGE
        && request.actionType() != ActionType.NOTIFY_LEADER) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "actionType invalid");
    }
    if (request.priority() == null || request.priority() < 1 || request.priority() > 100) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "priority must be 1-100");
    }
  }

  private void validateName(String name, FollowupRule existing) {
    if (name == null || name.isBlank() || name.trim().length() > 100) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "name required and max 100 chars");
    }
    if (ruleRepository.nameExists(name.trim(), existing == null ? null : existing.id())) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "rule name already exists");
    }
  }

  private void validateJson(RuleRequest request) {
    try {
      validateConditionComplexity(objectMapper.readTree(request.conditionJson()));
      objectMapper.readTree(request.actionConfig() == null || request.actionConfig().isBlank() ? "{}" : request.actionConfig());
    } catch (FollowupException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "conditionJson format invalid", ex);
    }
  }

  private void validateConditionComplexity(JsonNode root) {
    if (root == null || !root.isObject()) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "conditionJson must be object");
    }
    int conditions = countArray(root.path("conditions"));
    int orGroups = countArray(root.path("orGroups"));
    if (root.path("conditions").isMissingNode() && root.path("orGroups").isMissingNode()) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "conditionJson missing conditions");
    }
    if (conditions > MAX_AND_CONDITIONS || orGroups > MAX_OR_GROUPS) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "condition combination too complex");
    }
    if (orGroups > 0) {
      for (JsonNode group : root.path("orGroups")) {
        if (countArray(group.path("conditions")) > MAX_AND_CONDITIONS) {
          throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "condition group too complex");
        }
      }
    }
  }

  private int countArray(JsonNode node) {
    return node != null && node.isArray() ? node.size() : 0;
  }

  private FollowupRule require(long id) {
    return ruleRepository.findById(id)
        .orElseThrow(() -> new FollowupException(FollowupErrorCodes.BAD_REQUEST, "rule not found"));
  }
}
