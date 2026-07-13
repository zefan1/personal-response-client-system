package com.privateflow.modules.match.service;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.match.CustomerMatchErrorCodes;
import com.privateflow.modules.match.CustomerMatchException;
import com.privateflow.modules.match.CustomerSearchResult;
import com.privateflow.modules.match.CustomerSummary;
import com.privateflow.modules.match.util.PhoneUtils;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CustomerSearchService {

  private final CustomerQueryService customerQueryService;
  private final CustomerSummaryMapper summaryMapper;
  private final CustomerAccessService customerAccessService;

  public CustomerSearchService(
      CustomerQueryService customerQueryService,
      CustomerSummaryMapper summaryMapper,
      CustomerAccessService customerAccessService) {
    this.customerQueryService = customerQueryService;
    this.summaryMapper = summaryMapper;
    this.customerAccessService = customerAccessService;
  }

  public CustomerSearchResult search(String q, int limit) {
    if (q == null || q.isBlank()) {
      throw new CustomerMatchException(CustomerMatchErrorCodes.BAD_REQUEST, "搜索关键词不能为空");
    }
    if (limit < 1 || limit > 50) {
      throw new CustomerMatchException(CustomerMatchErrorCodes.BAD_REQUEST, "limit 必须在 1-50 之间");
    }
    try {
      String cleanedPhone = PhoneUtils.clean(q);
      List<Customer> customers;
      if (PhoneUtils.isValid(cleanedPhone)) {
        Customer exact = customerQueryService.getByPhone(cleanedPhone);
        customers = exact == null ? List.of() : List.of(exact);
      } else {
        customers = customerQueryService.searchByKeyword(q.trim(), limit).stream()
            .sorted(Comparator.comparing(Customer::getLastFollowupAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
      }
      List<CustomerSummary> summaries = customers.stream()
          .filter(customerAccessService::canAccess)
          .filter(customer -> PhoneUtils.isValid(customer.getPhone()))
          .map(customer -> summaryMapper.toSummary(customer, null))
          .toList();
      return new CustomerSearchResult(summaries, summaries.size());
    } catch (CustomerMatchException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new CustomerMatchException(
          CustomerMatchErrorCodes.MATCH_FAILED,
          "客户搜索服务暂不可用",
          ex);
    }
  }
}
