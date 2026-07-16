package com.privateflow.modules.followup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.followup.ActionType;
import com.privateflow.modules.followup.FollowupRule;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RuleMatcherTest {

  @Test
  void matcherLoadsTagContextOncePerCustomer() {
    ConditionEvaluator evaluator = Mockito.mock(ConditionEvaluator.class);
    FollowupTagContextLoader loader = Mockito.mock(FollowupTagContextLoader.class);
    RuleMatcher matcher = new RuleMatcher(evaluator, loader);
    Customer customer = new Customer();
    customer.setId(7L);
    FollowupTagContext context = FollowupTagContext.of(Map.of(50L, Set.of(51L)));
    when(loader.load(customer)).thenReturn(context);
    when(evaluator.matches(eq(customer), any(String.class), eq(context))).thenReturn(true);

    matcher.match(customer, List.of(rule(1L), rule(2L), rule(3L)));

    verify(loader).load(customer);
    verify(evaluator, times(3)).matches(eq(customer), eq("{}"), eq(context));
  }

  @Test
  void tagContextFailureKeepsOrdinaryRuleEvaluationRunning() {
    ConditionEvaluator evaluator = Mockito.mock(ConditionEvaluator.class);
    FollowupTagContextLoader loader = Mockito.mock(FollowupTagContextLoader.class);
    RuleMatcher matcher = new RuleMatcher(evaluator, loader);
    Customer customer = new Customer();
    customer.setId(7L);
    when(loader.load(customer)).thenThrow(new IllegalStateException("directory unavailable"));
    when(evaluator.matches(customer, "{}", FollowupTagContext.empty())).thenReturn(true);

    assertThat(matcher.match(customer, List.of(rule(1L)))).hasSize(1);
  }

  private FollowupRule rule(long id) {
    return new FollowupRule(id, "rule-" + id, "{}", ActionType.ALERT, "{}", 10, true, false, null, null);
  }
}
