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
public class FullScanScheduler {

  private final CustomerQueryService customerQueryService;
  private final RuleLoader ruleLoader;
  private final RuleMatcher ruleMatcher;
  private final ActionExecutor actionExecutor;
  private final FollowupConfigProvider configProvider;

  public FullScanScheduler(
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

  @Scheduled(cron = "${followup.full-scan-cron:0 0 9 * * *}")
  public void scan() {
    List<FollowupRule> snapshot = ruleLoader.takeSnapshot();
    ScanFilter filter = new ScanFilter(null, null, null, true, configProvider.get().scanBatchSize());
    for (Customer customer : customerQueryService.scanActiveCustomers(filter)) {
      actionExecutor.execute(customer, ruleMatcher.match(customer, snapshot));
    }
  }
}
