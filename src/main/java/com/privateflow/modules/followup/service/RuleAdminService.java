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
  private final ConditionEvaluator conditionEvaluator;

  public RuleAdminService(
      FollowupRuleRepository ruleRepository,
      ObjectMapper objectMapper,
      RuleLoader ruleLoader,
      AuditLogger auditLogger,
      ConditionEvaluator conditionEvaluator) {
    this.ruleRepository = ruleRepository;
    this.objectMapper = objectMapper;
    this.ruleLoader = ruleLoader;
    this.auditLogger = auditLogger;
    this.conditionEvaluator = conditionEvaluator;
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
      throw new FollowupException(FollowupErrorCodes.FORBIDDEN, "内置规则不能删除，请改为停用");
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
        .orElseThrow(() -> new FollowupException(FollowupErrorCodes.BAD_REQUEST, "规则不存在"));
  }

  private void validate(RuleRequest request, boolean builtinUpdate, FollowupRule existing) {
    if (request == null) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "请求内容不能为空");
    }
    if (!builtinUpdate) {
      validateName(request.name(), existing);
    }
    if (builtinUpdate && existing != null && request.name() != null && !request.name().isBlank()
        && !existing.name().equals(request.name())) {
      throw new FollowupException(FollowupErrorCodes.FORBIDDEN, "内置规则名称不能修改");
    }
    if (builtinUpdate && existing != null && request.actionType() != null && existing.actionType() != request.actionType()) {
      throw new FollowupException(FollowupErrorCodes.FORBIDDEN, "内置规则动作不能修改");
    }
    if (request.conditionJson() == null || request.conditionJson().isBlank()) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "规则条件不能为空");
    }
    validateJson(request);
    if (!builtinUpdate && request.actionType() == null) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "请选择规则动作");
    }
    if (!builtinUpdate && request.actionType() != ActionType.ALERT
        && request.actionType() != ActionType.TAG_CHANGE
        && request.actionType() != ActionType.NOTIFY_LEADER) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "规则动作不合法");
    }
    if (request.priority() == null || request.priority() < 1 || request.priority() > 100) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "优先级必须在 1-100 之间");
    }
  }

  private void validateName(String name, FollowupRule existing) {
    if (name == null || name.isBlank() || name.trim().length() > 100) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "规则名称不能为空且不能超过 100 个字符");
    }
    if (ruleRepository.nameExists(name.trim(), existing == null ? null : existing.id())) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "规则名称已存在");
    }
  }

  private void validateJson(RuleRequest request) {
    try {
      JsonNode condition = objectMapper.readTree(request.conditionJson());
      validateConditionComplexity(condition);
      conditionEvaluator.validateDefinition(condition);
      objectMapper.readTree(request.actionConfig() == null || request.actionConfig().isBlank() ? "{}" : request.actionConfig());
    } catch (FollowupException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "规则条件格式不正确", ex);
    }
  }

  private void validateConditionComplexity(JsonNode root) {
    if (root == null || !root.isObject()) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "规则条件必须是对象格式");
    }
    int conditions = countArray(root.path("conditions"));
    int orGroups = countArray(root.path("orGroups"));
    if (root.path("conditions").isMissingNode() && root.path("orGroups").isMissingNode()) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "规则条件缺少判断项");
    }
    if (conditions > MAX_AND_CONDITIONS || orGroups > MAX_OR_GROUPS) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "规则条件组合过多，请简化后保存");
    }
    if (orGroups > 0) {
      for (JsonNode group : root.path("orGroups")) {
        if (countArray(group.path("conditions")) > MAX_AND_CONDITIONS) {
          throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "规则条件分组过多，请简化后保存");
        }
      }
    }
  }

  private int countArray(JsonNode node) {
    return node != null && node.isArray() ? node.size() : 0;
  }

  private FollowupRule require(long id) {
    return ruleRepository.findById(id)
        .orElseThrow(() -> new FollowupException(FollowupErrorCodes.BAD_REQUEST, "规则不存在"));
  }
}
