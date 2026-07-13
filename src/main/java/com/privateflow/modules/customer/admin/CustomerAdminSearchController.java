package com.privateflow.modules.customer.admin;

import com.privateflow.modules.match.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerAdminSearchController {

  private final CustomerAdminSearchService service;

  public CustomerAdminSearchController(CustomerAdminSearchService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/v1/customers/search")
  public ApiResponse<CustomerAdminSearchPage> search(
      @RequestParam(value = "q", defaultValue = "") String keyword,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.search(keyword, page, pageSize));
  }
}
