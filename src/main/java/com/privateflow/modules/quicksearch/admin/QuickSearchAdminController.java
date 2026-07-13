package com.privateflow.modules.quicksearch.admin;

import com.privateflow.modules.match.ApiResponse;
import java.util.Map;
import com.privateflow.modules.quicksearch.ContentType;
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
  public ApiResponse<Map<String, Object>> list(
      @RequestParam(value = "contentType", required = false) ContentType contentType,
      @RequestParam(value = "leadType", required = false) String leadType,
      @RequestParam(value = "enabled", required = false) Boolean enabled,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size,
      @RequestParam(value = "sortBy", required = false) String sortBy,
      @RequestParam(value = "sortDir", required = false) String sortDir) {
    return ApiResponse.ok(service.list(new QuickSearchAdminListQuery(
        contentType,
        leadType,
        enabled,
        keyword,
        page,
        size,
        sortBy,
        sortDir)));
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
