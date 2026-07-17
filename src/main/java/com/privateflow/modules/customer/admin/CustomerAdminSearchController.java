package com.privateflow.modules.customer.admin;

import com.privateflow.modules.match.ApiResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

  @PostMapping("/admin/api/v1/customers/search")
  public ApiResponse<CustomerAdminSearchPage> search(@RequestBody CustomerSearchRequest request) {
    return ApiResponse.ok(service.search(request));
  }

  @PostMapping("/admin/api/v1/customers/export")
  public ResponseEntity<byte[]> export(@RequestBody(required = false) CustomerSearchRequest request) {
    byte[] content = service.export(request);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
    headers.setContentDisposition(ContentDisposition.attachment()
        .filename("customers.csv", StandardCharsets.UTF_8).build());
    return new ResponseEntity<>(content, headers, HttpStatus.OK);
  }
}
