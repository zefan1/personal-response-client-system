package com.privateflow.modules.api.web;

import com.privateflow.modules.api.config.ConfigAdminService;
import com.privateflow.modules.match.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/v1/configs")
public class ConfigController {

  private final ConfigAdminService configAdminService;

  public ConfigController(ConfigAdminService configAdminService) {
    this.configAdminService = configAdminService;
  }

  @GetMapping
  public ApiResponse<Map<String, String>> list(@RequestParam(value = "prefix", required = false) String prefix) {
    return ApiResponse.ok(configAdminService.list(prefix));
  }

  @PutMapping("/{key:.+}")
  public ApiResponse<Map<String, Object>> update(@PathVariable("key") String key, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(configAdminService.update(key, body));
  }
}
