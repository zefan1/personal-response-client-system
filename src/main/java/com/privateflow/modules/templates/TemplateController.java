package com.privateflow.modules.templates;

import com.privateflow.modules.match.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TemplateController {

  private final PersonalTemplateService personalTemplateService;

  public TemplateController(PersonalTemplateService personalTemplateService) {
    this.personalTemplateService = personalTemplateService;
  }

  @PostMapping("/api/v1/templates/personal")
  public ApiResponse<PersonalTemplate> save(@RequestBody PersonalTemplateRequest request) {
    return ApiResponse.ok(personalTemplateService.save(request));
  }

  @GetMapping("/api/v1/templates/personal")
  public ApiResponse<List<PersonalTemplate>> mine() {
    return ApiResponse.ok(personalTemplateService.listMine());
  }

  @GetMapping("/api/v1/templates/team")
  public ApiResponse<List<TeamTemplate>> team() {
    return ApiResponse.ok(personalTemplateService.listTeamTemplates());
  }

  @PostMapping("/api/v1/templates/personal/{id}/use")
  public ApiResponse<Map<String, Object>> usePersonal(@PathVariable("id") long templateId) {
    return ApiResponse.ok(personalTemplateService.recordPersonalTemplateUse(templateId));
  }

  @PostMapping("/api/v1/templates/team/{id}/use")
  public ApiResponse<Map<String, Object>> useTeam(@PathVariable("id") long quickSearchItemId) {
    return ApiResponse.ok(personalTemplateService.recordTeamTemplateUse(quickSearchItemId));
  }
}
