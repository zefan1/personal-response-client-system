package com.privateflow.modules.followup.service;

import com.privateflow.common.events.NewLeadEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class NewLeadEventListener {

  private final CustomerQueryService customerQueryService;
  private final ActionExecutor actionExecutor;

  public NewLeadEventListener(CustomerQueryService customerQueryService, ActionExecutor actionExecutor) {
    this.customerQueryService = customerQueryService;
    this.actionExecutor = actionExecutor;
  }

  @Async("profileUpdateExecutor")
  @EventListener
  public void onNewLead(NewLeadEvent event) {
    if (event == null || event.phone() == null) {
      return;
    }
    Customer customer = customerQueryService.getByPhone(event.phone());
    actionExecutor.executeNewLead(customer, event.sourceTable());
  }
}
