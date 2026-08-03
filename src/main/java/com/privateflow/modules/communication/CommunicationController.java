package com.privateflow.modules.communication;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.match.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/communications")
public class CommunicationController {

  private final CommunicationAccessService service;

  public CommunicationController(CommunicationAccessService service) {
    this.service = service;
  }

  @GetMapping("/customers/{phone}/messages")
  public ApiResponse<CommunicationMessagePage> messages(
      @PathVariable("phone") String phone,
      @RequestParam(value = "platform", required = false) String platform,
      @RequestParam(value = "from", required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(value = "to", required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "beforeId", required = false) Long beforeId,
      @RequestParam(value = "limit", defaultValue = "50") int limit) {
    return ApiResponse.ok(service.listMessages(
        phone, platform, from, to, keyword, beforeId, limit));
  }

  @GetMapping("/customers/by-id/{customerId}/messages")
  public ApiResponse<CommunicationMessagePage> messagesByCustomerId(
      @PathVariable("customerId") long customerId,
      @RequestParam(value = "platform", required = false) String platform,
      @RequestParam(value = "from", required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(value = "to", required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "beforeId", required = false) Long beforeId,
      @RequestParam(value = "limit", defaultValue = "50") int limit) {
    return ApiResponse.ok(service.listMessages(
        customerId, platform, from, to, keyword, beforeId, limit));
  }

  @PutMapping("/messages/{messageId}")
  public ApiResponse<Map<String, Boolean>> correct(
      @PathVariable("messageId") long messageId,
      @RequestBody CommunicationCorrectionRequest request) {
    service.correct(messageId, request == null ? null : request.correctedText());
    return ApiResponse.ok(Map.of("corrected", true));
  }

  @GetMapping("/customers/{phone}/summaries")
  public ApiResponse<List<CommunicationSummaryVersion>> summaries(
      @PathVariable("phone") String phone) {
    return ApiResponse.ok(service.listSummaryVersions(phone));
  }

  @GetMapping("/customers/by-id/{customerId}/summaries")
  public ApiResponse<List<CommunicationSummaryVersion>> summariesByCustomerId(
      @PathVariable("customerId") long customerId) {
    return ApiResponse.ok(service.listSummaryVersions(customerId));
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    if (ApiErrorCodes.BAD_REQUEST.equals(exception.getErrorCode())) {
      status = HttpStatus.BAD_REQUEST;
    } else if (ApiErrorCodes.FORBIDDEN.equals(exception.getErrorCode())) {
      status = HttpStatus.FORBIDDEN;
    } else if (ApiErrorCodes.CONFLICT.equals(exception.getErrorCode())) {
      status = HttpStatus.CONFLICT;
    }
    return ResponseEntity.status(status)
        .body(ApiResponse.error(exception.getErrorCode(), exception.getMessage()));
  }
}
