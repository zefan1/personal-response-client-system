package com.privateflow.modules.api.ai;

import com.privateflow.modules.match.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiConfigController {

  private final AiEnvironmentService environmentService;
  private final PromptVersionService promptVersionService;

  public AiConfigController(AiEnvironmentService environmentService, PromptVersionService promptVersionService) {
    this.environmentService = environmentService;
    this.promptVersionService = promptVersionService;
  }

  @GetMapping("/admin/api/v1/skill-environments")
  public ApiResponse<List<AiEnvironment>> skillEnvironments() {
    return ApiResponse.ok(environmentService.list(AiEnvironmentType.SKILL));
  }

  @PostMapping("/admin/api/v1/skill-environments")
  public ApiResponse<AiEnvironment> createSkill(@RequestBody AiEnvironmentRequest request) {
    return ApiResponse.ok(environmentService.create(AiEnvironmentType.SKILL, request));
  }

  @PutMapping("/admin/api/v1/skill-environments/{id}")
  public ApiResponse<AiEnvironment> updateSkill(@PathVariable("id") long id, @RequestBody AiEnvironmentRequest request) {
    return ApiResponse.ok(environmentService.update(AiEnvironmentType.SKILL, id, request));
  }

  @PutMapping("/admin/api/v1/skill-environments/{id}/activate")
  public ApiResponse<AiEnvironment> activateSkill(@PathVariable("id") long id) {
    return ApiResponse.ok(environmentService.activate(AiEnvironmentType.SKILL, id));
  }

  @DeleteMapping("/admin/api/v1/skill-environments/{id}")
  public ApiResponse<Void> deleteSkill(@PathVariable("id") long id) {
    environmentService.delete(AiEnvironmentType.SKILL, id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/admin/api/v1/image-environments")
  public ApiResponse<List<AiEnvironment>> imageEnvironments() {
    return ApiResponse.ok(environmentService.list(AiEnvironmentType.IMAGE));
  }

  @PostMapping("/admin/api/v1/image-environments")
  public ApiResponse<AiEnvironment> createImage(@RequestBody AiEnvironmentRequest request) {
    return ApiResponse.ok(environmentService.create(AiEnvironmentType.IMAGE, request));
  }

  @PutMapping("/admin/api/v1/image-environments/{id}")
  public ApiResponse<AiEnvironment> updateImage(@PathVariable("id") long id, @RequestBody AiEnvironmentRequest request) {
    return ApiResponse.ok(environmentService.update(AiEnvironmentType.IMAGE, id, request));
  }

  @PutMapping("/admin/api/v1/image-environments/{id}/activate")
  public ApiResponse<AiEnvironment> activateImage(@PathVariable("id") long id) {
    return ApiResponse.ok(environmentService.activate(AiEnvironmentType.IMAGE, id));
  }

  @DeleteMapping("/admin/api/v1/image-environments/{id}")
  public ApiResponse<Void> deleteImage(@PathVariable("id") long id) {
    environmentService.delete(AiEnvironmentType.IMAGE, id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/admin/api/v1/image-environments/{id}/test")
  public ApiResponse<ImageEnvironmentTestResponse> testImage(@PathVariable("id") long id) {
    return ApiResponse.ok(environmentService.testImage(id));
  }

  @GetMapping("/admin/api/v1/llm-environments")
  public ApiResponse<List<AiEnvironment>> llmEnvironments() {
    return ApiResponse.ok(environmentService.list(AiEnvironmentType.LLM));
  }

  @PostMapping("/admin/api/v1/llm-environments")
  public ApiResponse<AiEnvironment> createLlm(@RequestBody AiEnvironmentRequest request) {
    return ApiResponse.ok(environmentService.create(AiEnvironmentType.LLM, request));
  }

  @PutMapping("/admin/api/v1/llm-environments/{id}")
  public ApiResponse<AiEnvironment> updateLlm(@PathVariable("id") long id, @RequestBody AiEnvironmentRequest request) {
    return ApiResponse.ok(environmentService.update(AiEnvironmentType.LLM, id, request));
  }

  @PutMapping("/admin/api/v1/llm-environments/{id}/activate")
  public ApiResponse<AiEnvironment> activateLlm(@PathVariable("id") long id) {
    return ApiResponse.ok(environmentService.activate(AiEnvironmentType.LLM, id));
  }

  @DeleteMapping("/admin/api/v1/llm-environments/{id}")
  public ApiResponse<Void> deleteLlm(@PathVariable("id") long id) {
    environmentService.delete(AiEnvironmentType.LLM, id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/admin/api/v1/llm-environments/{id}/test")
  public ApiResponse<ImageEnvironmentTestResponse> testLlm(@PathVariable("id") long id) {
    return ApiResponse.ok(environmentService.testLlm(id));
  }

  @GetMapping("/admin/api/v1/skill-prompt/{type}/versions")
  public ApiResponse<PromptVersionPage> promptVersions(@PathVariable("type") String type) {
    return ApiResponse.ok(promptVersionService.list(type));
  }

  @PostMapping("/admin/api/v1/skill-prompt/{type}/restore")
  public ApiResponse<Map<String, Object>> restorePrompt(@PathVariable("type") String type, @RequestBody PromptRestoreRequest request) {
    return ApiResponse.ok(promptVersionService.restore(type, request));
  }
}
