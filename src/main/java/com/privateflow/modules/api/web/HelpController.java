package com.privateflow.modules.api.web;

import com.privateflow.modules.api.help.HelpRequestPayload;
import com.privateflow.modules.api.help.HelpResolvePayload;
import com.privateflow.modules.api.help.HelpService;
import com.privateflow.modules.match.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/help")
public class HelpController {

  private final HelpService helpService;

  public HelpController(HelpService helpService) {
    this.helpService = helpService;
  }

  @PostMapping("/request")
  public ApiResponse<Map<String, Object>> request(@RequestBody HelpRequestPayload payload) {
    return ApiResponse.ok(helpService.request(payload));
  }

  @PostMapping("/resolve")
  public ApiResponse<Map<String, Object>> resolve(@RequestBody HelpResolvePayload payload) {
    return ApiResponse.ok(helpService.resolve(payload));
  }
}
