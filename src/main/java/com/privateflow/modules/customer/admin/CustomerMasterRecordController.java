package com.privateflow.modules.customer.admin;

import com.privateflow.modules.match.ApiResponse;
import com.privateflow.modules.customer.history.CustomerFieldHistoryEntry;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerMasterRecordController {

  private final CustomerMasterRecordService service;

  public CustomerMasterRecordController(CustomerMasterRecordService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/v1/customer-master/default")
  public ApiResponse<Map<String, CustomerMasterRecord>> defaultRecord() {
    Map<String, CustomerMasterRecord> result = new LinkedHashMap<>();
    result.put("record", service.defaultRecord());
    return ApiResponse.ok(result);
  }

  @GetMapping("/admin/api/v1/customer-master/search")
  public ApiResponse<Map<String, List<CustomerMasterCandidate>>> search(
      @RequestParam(value = "q", defaultValue = "") String keyword) {
    return ApiResponse.ok(Map.of("items", service.search(keyword)));
  }

  @GetMapping("/admin/api/v1/customer-master/{customerId}")
  public ApiResponse<CustomerMasterRecord> record(@PathVariable("customerId") long customerId) {
    return ApiResponse.ok(service.record(customerId));
  }

  @GetMapping("/admin/api/v1/customer-master/{customerId}/fields/{fieldName}/history")
  public ApiResponse<List<CustomerFieldHistoryEntry>> history(
      @PathVariable("customerId") long customerId,
      @PathVariable("fieldName") String fieldName) {
    return ApiResponse.ok(service.history(customerId, fieldName));
  }
}
