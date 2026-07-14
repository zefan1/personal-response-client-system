package com.privateflow.modules.tags;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.service.CustomerAccessService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CustomerTagQueryService {

  private final CustomerTagFoundationRepository tagRepository;
  private final CustomerRepository customerRepository;
  private final CustomerAccessService accessService;

  public CustomerTagQueryService(
      CustomerTagFoundationRepository tagRepository,
      CustomerRepository customerRepository,
      CustomerAccessService accessService) {
    this.tagRepository = tagRepository;
    this.customerRepository = customerRepository;
    this.accessService = accessService;
  }

  public List<CustomerTagQueryDto> current(long customerId) {
    requireAccessibleCustomer(customerId);
    return List.copyOf(tagRepository.findCurrentTagDetails(customerId));
  }

  public List<CustomerTagQueryDto> history(long customerId, int limit) {
    requireAccessibleCustomer(customerId);
    int safeLimit = Math.max(1, Math.min(limit, 1000));
    return List.copyOf(tagRepository.findTagHistoryDetails(customerId, safeLimit));
  }

  private void requireAccessibleCustomer(long customerId) {
    if (customerId <= 0) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "客户编号必须大于 0");
    }
    Customer customer = customerRepository.findById(customerId)
        .orElseThrow(() -> new ApiException(
            ApiErrorCodes.BAD_REQUEST,
            "客户不存在：" + customerId));
    if (!accessService.canAccess(customer)) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "无权查看该客户标签");
    }
  }
}
