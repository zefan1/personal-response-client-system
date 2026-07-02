package com.privateflow.modules.followup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

  private final FollowupRuleRepository ruleRepository;
  private final ObjectMapper objectMapper;
  private final RuleLoader ruleLoader;

  public RuleAdminService(FollowupRuleRepository ruleRepository, ObjectMapper objectMapper, RuleLoader ruleLoader) {
    this.ruleRepository = ruleRepository;
    this.objectMapper = objectMapper;
    this.ruleLoader = ruleLoader;
  }

  public RulePage search(RuleSearchCriteria criteria) {
    return ruleRepository.search(criteria);
  }

  public FollowupRule create(RuleRequest request) {
    validate(request, false);
    long id = ruleRepository.create(request);
    ruleLoader.refresh();
    return ruleRepository.findById(id).orElseThrow();
  }

  public FollowupRule update(long id, RuleRequest request) {
    FollowupRule existing = ruleRepository.findById(id)
        .orElseThrow(() -> new FollowupException(FollowupErrorCodes.BAD_REQUEST, "规则不存在"));
    validate(request, existing.builtin());
    ruleRepository.update(id, request, existing.builtin());
    ruleLoader.refresh();
    return ruleRepository.findById(id).orElseThrow();
  }

  public void delete(long id) {
    FollowupRule existing = ruleRepository.findById(id)
        .orElseThrow(() -> new FollowupException(FollowupErrorCodes.BAD_REQUEST, "规则不存在"));
    if (existing.builtin()) {
      throw new FollowupException(FollowupErrorCodes.FORBIDDEN, "内置规则不可删除，可以停用");
    }
    ruleRepository.delete(id);
    ruleLoader.refresh();
  }

  public FollowupRule toggle(long id, boolean enabled) {
    ruleRepository.toggle(id, enabled);
    ruleLoader.refresh();
    return ruleRepository.findById(id).orElseThrow(() -> new FollowupException(FollowupErrorCodes.BAD_REQUEST, "规则不存在"));
  }

  private void validate(RuleRequest request, boolean builtinUpdate) {
    if (!builtinUpdate && (request.name() == null || request.name().isBlank() || request.name().length() > 100)) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "规则名称必填且不能超过 100 字符");
    }
    if (request.conditionJson() == null || request.conditionJson().isBlank()) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "conditionJson 必填");
    }
    try {
      objectMapper.readTree(request.conditionJson());
      objectMapper.readTree(request.actionConfig() == null || request.actionConfig().isBlank() ? "{}" : request.actionConfig());
    } catch (Exception ex) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "conditionJson 格式非法", ex);
    }
    if (!builtinUpdate && request.actionType() == null) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "actionType 必填");
    }
    if (!builtinUpdate && request.actionType() != ActionType.ALERT
        && request.actionType() != ActionType.TAG_CHANGE
        && request.actionType() != ActionType.NOTIFY_LEADER) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "actionType 参数不合法");
    }
    if (request.priority() == null || request.priority() < 1 || request.priority() > 100) {
      throw new FollowupException(FollowupErrorCodes.BAD_REQUEST, "priority 必须在 1-100 之间");
    }
  }
}
