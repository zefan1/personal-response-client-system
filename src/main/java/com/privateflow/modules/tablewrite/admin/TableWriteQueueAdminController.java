package com.privateflow.modules.tablewrite.admin;

import com.privateflow.modules.match.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TableWriteQueueAdminController {

  private final TableWriteQueueAdminService service;

  public TableWriteQueueAdminController(TableWriteQueueAdminService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/v1/table-writes/failed")
  public ApiResponse<List<Map<String, Object>>> listFailed(
      @RequestParam(value = "limit", defaultValue = "50") int limit) {
    return ApiResponse.ok(service.listFailed(limit));
  }

  @PostMapping("/admin/api/v1/table-writes/{id}/requeue")
  public ApiResponse<Map<String, Object>> requeue(@PathVariable("id") long id) {
    return ApiResponse.ok(service.requeueFailed(id));
  }

  @PostMapping("/admin/api/v1/table-writes/{id}/resolve")
  public ApiResponse<Map<String, Object>> resolve(@PathVariable("id") long id) {
    return ApiResponse.ok(service.resolveFailed(id));
  }
}
