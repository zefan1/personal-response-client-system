package com.privateflow.modules.templates;

import com.privateflow.modules.match.ApiResponse;
import java.util.List;
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
}
