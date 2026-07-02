package com.privateflow.modules.followup.service;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.followup.FollowupRule;
import com.privateflow.modules.followup.infra.FollowupRuleRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RuleLoader {

  private final FollowupRuleRepository ruleRepository;
  private final AtomicReference<List<FollowupRule>> rules = new AtomicReference<>(List.of());

  public RuleLoader(FollowupRuleRepository ruleRepository) {
    this.ruleRepository = ruleRepository;
  }

  @PostConstruct
  public void loadOnStart() {
    refresh();
  }

  @Scheduled(fixedDelayString = "${followup.rule-refresh-interval-s:30}000")
  public void refresh() {
    rules.set(List.copyOf(ruleRepository.findEnabled()));
  }

  @EventListener
  public void onConfigChanged(ConfigChangedEvent event) {
    if (event.configKey() != null && (event.configKey().startsWith("followup.") || event.configKey().startsWith("rules."))) {
      refresh();
    }
  }

  public List<FollowupRule> getEnabledRules() {
    return rules.get();
  }

  public List<FollowupRule> takeSnapshot() {
    return List.copyOf(rules.get());
  }
}
