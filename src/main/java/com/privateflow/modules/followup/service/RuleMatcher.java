package com.privateflow.modules.followup.service;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.followup.FollowupRule;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RuleMatcher {

  private static final Logger log = LoggerFactory.getLogger(RuleMatcher.class);
  private final ConditionEvaluator conditionEvaluator;

  public RuleMatcher(ConditionEvaluator conditionEvaluator) {
    this.conditionEvaluator = conditionEvaluator;
  }

  public List<RuleMatch> match(Customer customer, List<FollowupRule> rules) {
    List<RuleMatch> matches = new ArrayList<>();
    for (FollowupRule rule : rules) {
      try {
        if (conditionEvaluator.matches(customer, rule.conditionJson())) {
          matches.add(new RuleMatch(rule));
        }
      } catch (RuntimeException ex) {
        log.error("followup rule condition failed, ruleId={}", rule.id(), ex);
      }
    }
    return matches;
  }
}
