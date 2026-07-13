package com.privateflow.modules.llm;

import com.privateflow.modules.match.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LlmAdminController {

  private final LlmRoutingService routingService;
  private final LlmCallAnalyticsRepository analyticsRepository;

  public LlmAdminController(LlmRoutingService routingService, LlmCallAnalyticsRepository analyticsRepository) {
    this.routingService = routingService;
    this.analyticsRepository = analyticsRepository;
  }

  @GetMapping("/admin/api/v1/llm-routes")
  public ApiResponse<List<LlmSceneRoute>> list(
      @RequestParam(value = "scene", required = false) LlmScene scene,
      @RequestParam(value = "leadType", required = false) String leadType) {
    return ApiResponse.ok(routingService.list(scene, leadType));
  }

  @GetMapping("/admin/api/v1/llm-routes/scenes")
  public ApiResponse<List<LlmScene>> scenes() {
    return ApiResponse.ok(routingService.scenes());
  }

  @PostMapping("/admin/api/v1/llm-routes")
  public ApiResponse<LlmSceneRoute> create(@RequestBody LlmRouteRequest request) {
    return ApiResponse.ok(routingService.create(request));
  }

  @PutMapping("/admin/api/v1/llm-routes/{id}")
  public ApiResponse<LlmSceneRoute> update(@PathVariable("id") long id, @RequestBody LlmRouteRequest request) {
    return ApiResponse.ok(routingService.update(id, request));
  }

  @DeleteMapping("/admin/api/v1/llm-routes/{id}")
  public ApiResponse<Void> delete(@PathVariable("id") long id) {
    routingService.delete(id);
    return ApiResponse.ok(null);
  }

  @PutMapping("/admin/api/v1/llm-routes/{id}/toggle")
  public ApiResponse<LlmSceneRoute> toggle(@PathVariable("id") long id, @RequestBody LlmRouteToggleRequest request) {
    return ApiResponse.ok(routingService.toggle(id, request.enabled() != null && request.enabled()));
  }

  @GetMapping("/admin/api/v1/analytics/llm-calls")
  public ApiResponse<LlmCallAnalytics> analytics(
      @RequestParam(value = "days", defaultValue = "7") int days,
      @RequestParam(value = "scene", required = false) String scene,
      @RequestParam(value = "leadType", required = false) String leadType) {
    return ApiResponse.ok(analyticsRepository.query(days, scene, leadType));
  }
}
