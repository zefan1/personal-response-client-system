package com.privateflow.modules.followup.service;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.ScanFilter;
import com.privateflow.modules.followup.FollowupRule;
import com.privateflow.modules.followup.config.FollowupConfigProvider;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LightweightScanScheduler {

  private final CustomerQueryService customerQueryService;
  private final RuleLoader ruleLoader;
  private final RuleMatcher ruleMatcher;
  private final ActionExecutor actionExecutor;
  private final FollowupConfigProvider configProvider;

  public LightweightScanScheduler(
      CustomerQueryService customerQueryService,
      RuleLoader ruleLoader,
      RuleMatcher ruleMatcher,
      ActionExecutor actionExecutor,
      FollowupConfigProvider configProvider) {
    this.customerQueryService = customerQueryService;
    this.ruleLoader = ruleLoader;
    this.ruleMatcher = ruleMatcher;
    this.actionExecutor = actionExecutor;
    this.configProvider = configProvider;
  }

  @Scheduled(cron = "${followup.lightweight-scan-cron:0 0 * * * *}")
  public void scan() {
    List<FollowupRule> timelyRules = ruleLoader.takeSnapshot().stream()
        .filter(rule -> rule.conditionJson().contains("appointmentDate") || rule.conditionJson().contains("lastFollowupHours"))
        .toList();
    ScanFilter filter = new ScanFilter(2, true, null, true, Math.min(configProvider.get().scanBatchSize(), 1000));
    for (Customer customer : customerQueryService.scanActiveCustomers(filter)) {
      actionExecutor.execute(customer, ruleMatcher.match(customer, timelyRules));
    }
  }
}
