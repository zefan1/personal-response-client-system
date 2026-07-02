package com.privateflow.modules.match.service;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.match.Confidence;
import com.privateflow.modules.match.CustomerSummary;
import com.privateflow.modules.match.util.PhoneUtils;
import org.springframework.stereotype.Component;

@Component
public class CustomerSummaryMapper {

  public CustomerSummary toSummary(Customer customer, Confidence confidence) {
    return new CustomerSummary(
        PhoneUtils.mask(customer.getPhone()),
        customer.getNickname(),
        customer.getLeadType(),
        customer.getAssignedKeeper(),
        customer.getLastFollowupAt(),
        customer.getIntendedStore(),
        confidence);
  }
}
