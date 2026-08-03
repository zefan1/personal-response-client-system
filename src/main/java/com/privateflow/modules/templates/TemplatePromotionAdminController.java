package com.privateflow.modules.templates;

import com.privateflow.modules.match.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TemplatePromotionAdminController {

  private final TemplatePromotionService templatePromotionService;

  public TemplatePromotionAdminController(TemplatePromotionService templatePromotionService) {
    this.templatePromotionService = templatePromotionService;
  }

  @GetMapping("/admin/api/v1/template-promotion-candidates")
  public ApiResponse<List<TemplatePromotionCandidate>> list(
      @RequestParam(value = "status", required = false) TemplatePromotionCandidateStatus status) {
    return ApiResponse.ok(templatePromotionService.listCandidates(status));
  }

  @PostMapping("/admin/api/v1/template-promotion-candidates/{id}/publish")
  public ApiResponse<Map<String, Object>> publish(
      @PathVariable("id") long candidateId,
      @RequestBody PublishTeamTemplateRequest request) {
    return ApiResponse.ok(templatePromotionService.publish(candidateId, request));
  }

  @PostMapping("/admin/api/v1/template-promotion-candidates/{id}/not-publish")
  public ApiResponse<Void> notPublish(@PathVariable("id") long candidateId) {
    templatePromotionService.markNotPublished(candidateId);
    return ApiResponse.ok(null);
  }
}
