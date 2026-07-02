package com.privateflow.modules.match.service;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.match.CustomerMatchErrorCodes;
import com.privateflow.modules.match.CustomerMatchException;
import org.springframework.stereotype.Component;

@Component
public class ExactMatcher {

  private final CustomerQueryService customerQueryService;

  public ExactMatcher(CustomerQueryService customerQueryService) {
    this.customerQueryService = customerQueryService;
  }

  public Customer matchByPhone(String phone) {
    try {
      return customerQueryService.getByPhone(phone);
    } catch (RuntimeException ex) {
      throw new CustomerMatchException(
          CustomerMatchErrorCodes.MATCH_FAILED,
          "客户匹配服务暂不可用",
          ex);
    }
  }
}
