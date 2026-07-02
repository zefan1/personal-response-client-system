package com.privateflow.modules.analytics;

import com.privateflow.modules.match.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsController {

  private final AnalyticsService service;

  public AnalyticsController(AnalyticsService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/v1/analytics/overview")
  public ApiResponse<Map<String, Object>> overview(
      @RequestParam(value = "days", defaultValue = "7") int days,
      @RequestParam(value = "leadType", required = false) String leadType,
      @RequestParam(value = "caller", required = false) String caller) {
    return ApiResponse.ok(service.overview(days, leadType, caller));
  }

  @GetMapping("/admin/api/v1/analytics/funnels")
  public ApiResponse<Map<String, Object>> funnels(
      @RequestParam(value = "leadType", required = false) String leadType,
      @RequestParam(value = "caller", required = false) String caller) {
    return ApiResponse.ok(service.funnels(leadType, caller));
  }

  @GetMapping("/admin/api/v1/analytics/staff")
  public ApiResponse<Map<String, Object>> staff(
      @RequestParam(value = "days", defaultValue = "7") int days,
      @RequestParam(value = "leadType", required = false) String leadType,
      @RequestParam(value = "caller", required = false) String caller) {
    return ApiResponse.ok(service.staff(days, leadType, caller));
  }

  @GetMapping("/admin/api/v1/analytics/sources")
  public ApiResponse<Map<String, Object>> sources(
      @RequestParam(value = "days", defaultValue = "7") int days,
      @RequestParam(value = "leadType", required = false) String leadType,
      @RequestParam(value = "caller", required = false) String caller) {
    return ApiResponse.ok(service.sources(days, leadType, caller));
  }

  @GetMapping("/admin/api/v1/analytics/stages")
  public ApiResponse<Map<String, Object>> stages(
      @RequestParam(value = "leadType", required = false) String leadType,
      @RequestParam(value = "caller", required = false) String caller) {
    return ApiResponse.ok(service.stages(leadType, caller));
  }

  @GetMapping("/admin/api/v1/analytics/health")
  public ApiResponse<Map<String, Object>> health(
      @RequestParam(value = "days", defaultValue = "7") int days,
      @RequestParam(value = "leadType", required = false) String leadType,
      @RequestParam(value = "caller", required = false) String caller) {
    return ApiResponse.ok(service.health(days, leadType, caller));
  }

  @GetMapping("/admin/api/v1/analytics/lifecycle")
  public ApiResponse<Map<String, Object>> lifecycle(
      @RequestParam(value = "leadType", required = false) String leadType,
      @RequestParam(value = "caller", required = false) String caller) {
    return ApiResponse.ok(service.lifecycle(leadType, caller));
  }

  @GetMapping("/admin/api/v1/analytics/risks")
  public ApiResponse<Map<String, Object>> risks(
      @RequestParam(value = "days", defaultValue = "7") int days,
      @RequestParam(value = "leadType", required = false) String leadType,
      @RequestParam(value = "caller", required = false) String caller) {
    return ApiResponse.ok(service.risks(days, leadType, caller));
  }

  @GetMapping("/admin/api/v1/analytics/content-ranking")
  public ApiResponse<Map<String, Object>> contentRanking(
      @RequestParam(value = "days", defaultValue = "7") int days,
      @RequestParam(value = "leadType", required = false) String leadType,
      @RequestParam(value = "caller", required = false) String caller) {
    return ApiResponse.ok(service.contentRanking(days, leadType, caller));
  }
}
