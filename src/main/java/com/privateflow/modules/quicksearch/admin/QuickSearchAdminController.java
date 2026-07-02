package com.privateflow.modules.quicksearch.admin;

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
import org.springframework.web.multipart.MultipartFile;

@RestController
public class QuickSearchAdminController {

  private final QuickSearchAdminService service;

  public QuickSearchAdminController(QuickSearchAdminService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/v1/quick-search/items")
  public ApiResponse<List<QuickSearchAdminItem>> list() {
    return ApiResponse.ok(service.list());
  }

  @PostMapping("/admin/api/v1/quick-search/items")
  public ApiResponse<Map<String, Object>> create(@RequestBody QuickSearchItemRequest request) {
    return ApiResponse.ok(service.create(request));
  }

  @PutMapping("/admin/api/v1/quick-search/items/{id}")
  public ApiResponse<QuickSearchAdminItem> update(@PathVariable("id") long id, @RequestBody QuickSearchItemRequest request) {
    return ApiResponse.ok(service.update(id, request));
  }

  @PutMapping("/admin/api/v1/quick-search/items/{id}/toggle")
  public ApiResponse<Map<String, Object>> toggle(@PathVariable("id") long id) {
    return ApiResponse.ok(service.toggle(id));
  }

  @DeleteMapping("/admin/api/v1/quick-search/items/{id}")
  public ApiResponse<Void> delete(@PathVariable("id") long id) {
    service.delete(id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/admin/api/v1/upload/image")
  public ApiResponse<ImageUploadResponse> upload(@RequestParam("file") MultipartFile file) {
    return ApiResponse.ok(service.uploadImage(file));
  }
}
