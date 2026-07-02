package com.privateflow.modules.notices;

import com.privateflow.modules.match.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NoticeController {

  private final NoticeService service;

  public NoticeController(NoticeService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/v1/notices")
  public ApiResponse<Map<String, Object>> list(
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", required = false) Integer size,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "level", required = false) NoticeLevel level,
      @RequestParam(value = "source", required = false) NoticeSource source) {
    NoticeStatus noticeStatus = "STOPPED".equalsIgnoreCase(status) || status == null || status.isBlank()
        ? null
        : NoticeStatus.valueOf(status);
    return ApiResponse.ok(service.list(noticeStatus, level, source, status, page, size));
  }

  @PostMapping("/admin/api/v1/notices")
  public ApiResponse<SystemNotice> create(@RequestBody NoticeCreateRequest request) {
    return ApiResponse.ok(service.create(request));
  }

  @PutMapping("/admin/api/v1/notices/{id}")
  public ApiResponse<SystemNotice> update(@PathVariable("id") long id, @RequestBody NoticeUpdateRequest request) {
    return ApiResponse.ok(service.update(id, request));
  }

  @PutMapping("/admin/api/v1/notices/{id}/stop")
  public ApiResponse<SystemNotice> stop(@PathVariable("id") long id) {
    return ApiResponse.ok(service.stop(id));
  }

  @DeleteMapping("/admin/api/v1/notices/{id}")
  public ApiResponse<Void> delete(@PathVariable("id") long id) {
    service.delete(id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/api/v1/notices/active")
  public ApiResponse<List<Map<String, Object>>> active() {
    return ApiResponse.ok(service.active());
  }
}
