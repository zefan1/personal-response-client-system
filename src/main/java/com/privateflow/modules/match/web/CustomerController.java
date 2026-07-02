package com.privateflow.modules.match.web;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.match.ApiResponse;
import com.privateflow.modules.match.CustomerMatchErrorCodes;
import com.privateflow.modules.match.CustomerMatchException;
import com.privateflow.modules.match.CustomerSearchResult;
import com.privateflow.modules.profile.BatchResolveRequest;
import com.privateflow.modules.profile.BatchResolveResult;
import com.privateflow.modules.profile.CustomerProfileView;
import com.privateflow.modules.profile.ManualProfileUpdateRequest;
import com.privateflow.modules.profile.ManualProfileUpdateResult;
import com.privateflow.modules.profile.ProfileErrorCodes;
import com.privateflow.modules.profile.ProfileUpdateException;
import com.privateflow.modules.match.service.CustomerProfileService;
import com.privateflow.modules.match.service.CustomerSearchService;
import com.privateflow.modules.profile.service.ManualEditHandler;
import com.privateflow.modules.profile.service.SuggestionQueueManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

  private final CustomerSearchService customerSearchService;
  private final CustomerProfileService customerProfileService;
  private final ManualEditHandler manualEditHandler;
  private final SuggestionQueueManager suggestionQueueManager;

  public CustomerController(
      CustomerSearchService customerSearchService,
      CustomerProfileService customerProfileService,
      ManualEditHandler manualEditHandler,
      SuggestionQueueManager suggestionQueueManager) {
    this.customerSearchService = customerSearchService;
    this.customerProfileService = customerProfileService;
    this.manualEditHandler = manualEditHandler;
    this.suggestionQueueManager = suggestionQueueManager;
  }

  @GetMapping("/search")
  public ApiResponse<CustomerSearchResult> search(
      @RequestParam("q") String q,
      @RequestParam(value = "limit", defaultValue = "10") int limit) {
    return ApiResponse.ok(customerSearchService.search(q, limit));
  }

  @GetMapping("/{phone}")
  public ApiResponse<CustomerProfileView> profile(@PathVariable("phone") String phone) {
    return ApiResponse.ok(customerProfileService.getProfile(phone));
  }

  @PutMapping("/{phone}")
  public ApiResponse<ManualProfileUpdateResult> update(
      @PathVariable("phone") String phone,
      @RequestBody ManualProfileUpdateRequest request) {
    return ApiResponse.ok(manualEditHandler.update(phone, request));
  }

  @PostMapping("/{phone}/suggestions/batch-resolve")
  public ApiResponse<BatchResolveResult> batchResolve(
      @PathVariable("phone") String phone,
      @RequestBody BatchResolveRequest request) {
    return ApiResponse.ok(suggestionQueueManager.batchResolve(phone, request));
  }

  @ExceptionHandler(CustomerMatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleCustomerMatch(CustomerMatchException ex) {
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    if (CustomerMatchErrorCodes.BAD_REQUEST.equals(ex.getErrorCode())) {
      status = HttpStatus.BAD_REQUEST;
    } else if (CustomerMatchErrorCodes.CUSTOMER_NOT_FOUND.equals(ex.getErrorCode())) {
      status = HttpStatus.NOT_FOUND;
    }
    return ResponseEntity.status(status).body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
  }

  @ExceptionHandler(ProfileUpdateException.class)
  public ResponseEntity<ApiResponse<Void>> handleProfileUpdate(ProfileUpdateException ex) {
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    if (ProfileErrorCodes.BAD_REQUEST.equals(ex.getErrorCode())) {
      status = HttpStatus.BAD_REQUEST;
    } else if (ProfileErrorCodes.VERSION_CONFLICT.equals(ex.getErrorCode())) {
      status = HttpStatus.CONFLICT;
    }
    return ResponseEntity.status(status).body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
  }
}
