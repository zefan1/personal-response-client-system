package com.privateflow.modules.skill.admin;

import com.privateflow.modules.match.ApiResponse;
import com.privateflow.modules.skill.Scene;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SkillAdminController {

  private final SkillAdminService skillAdminService;
  private final SkillCallAnalyticsRepository analyticsRepository;

  public SkillAdminController(SkillAdminService skillAdminService, SkillCallAnalyticsRepository analyticsRepository) {
    this.skillAdminService = skillAdminService;
    this.analyticsRepository = analyticsRepository;
  }

  @GetMapping("/admin/api/v1/skills")
  public ApiResponse<List<SkillSceneBinding>> list(
      @RequestParam(value = "scene", required = false) Scene scene,
      @RequestParam(value = "leadType", required = false) String leadType) {
    return ApiResponse.ok(skillAdminService.list(scene, leadType));
  }

  @PostMapping("/admin/api/v1/skills")
  public ApiResponse<SkillSceneBinding> create(@RequestBody SkillBindingRequest request) {
    return ApiResponse.ok(skillAdminService.create(request));
  }

  @PutMapping("/admin/api/v1/skills/{id}")
  public ApiResponse<SkillSceneBinding> update(@PathVariable("id") long id, @RequestBody SkillBindingRequest request) {
    return ApiResponse.ok(skillAdminService.update(id, request));
  }

  @DeleteMapping("/admin/api/v1/skills/{id}")
  public ApiResponse<Void> delete(@PathVariable("id") long id) {
    skillAdminService.delete(id);
    return ApiResponse.ok(null);
  }

  @PutMapping("/admin/api/v1/skills/{id}/toggle")
  public ApiResponse<SkillToggleResponse> toggle(@PathVariable("id") long id, @RequestBody SkillToggleRequest request) {
    return ApiResponse.ok(skillAdminService.toggle(id, request.enabled()));
  }

  @GetMapping("/admin/api/v1/skills/available")
  public ApiResponse<List<AvailableSkill>> available() {
    return ApiResponse.ok(skillAdminService.availableSkills());
  }

  @PostMapping("/admin/api/v1/skills/{id}/test")
  public ApiResponse<SkillTestResponse> test(@PathVariable("id") long id, @RequestBody SkillTestRequest request) {
    return ApiResponse.ok(skillAdminService.test(id, request));
  }

  @GetMapping("/admin/api/v1/analytics/skill-calls")
  public ApiResponse<SkillCallAnalytics> analytics(
      @RequestParam(value = "days", defaultValue = "7") int days,
      @RequestParam(value = "scene", required = false) String scene,
      @RequestParam(value = "leadType", required = false) String leadType) {
    return ApiResponse.ok(analyticsRepository.query(days, scene, leadType));
  }

  @ExceptionHandler(SkillAdminException.class)
  public ResponseEntity<ApiResponse<Void>> handleSkillAdmin(SkillAdminException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
  }
}
