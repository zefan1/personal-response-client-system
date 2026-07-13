package com.privateflow.modules.tags;

import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.match.ApiResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TagAdminController {

  private final TagAdminService service;

  public TagAdminController(TagAdminService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/v1/tags/categories")
  public ApiResponse<Map<String, Object>> list() {
    return ApiResponse.ok(service.list());
  }

  @PostMapping("/admin/api/v1/tags/categories")
  public ApiResponse<TagCategory> createCategory(@RequestBody TagCategoryRequest request) {
    return ApiResponse.ok(service.createCategory(request));
  }

  @PutMapping("/admin/api/v1/tags/categories/{id}")
  public ApiResponse<TagCategory> updateCategory(@PathVariable("id") long id, @RequestBody TagCategoryRequest request) {
    return ApiResponse.ok(service.updateCategory(id, request));
  }

  @DeleteMapping("/admin/api/v1/tags/categories/{id}")
  public ApiResponse<Void> deleteCategory(@PathVariable("id") long id) {
    service.deleteCategory(id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/admin/api/v1/tags/values")
  public ApiResponse<TagValue> createValue(@RequestBody TagValueRequest request) {
    return ApiResponse.ok(service.createValue(request));
  }

  @PutMapping("/admin/api/v1/tags/values/{id}")
  public ApiResponse<TagValue> updateValue(@PathVariable("id") long id, @RequestBody TagValueRequest request) {
    return ApiResponse.ok(service.updateValue(id, request));
  }

  @PutMapping("/admin/api/v1/tags/values/{id}/toggle")
  public ApiResponse<TagValue> toggleValue(@PathVariable("id") long id, @RequestBody TagValueRequest request) {
    if (request == null || request.isEnabled() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请明确选择启用或停用");
    }
    return ApiResponse.ok(service.toggleValue(id, Boolean.TRUE.equals(request.isEnabled())));
  }

  @DeleteMapping("/admin/api/v1/tags/values/{id}")
  public ApiResponse<Void> deleteValue(@PathVariable("id") long id) {
    service.deleteValue(id);
    return ApiResponse.ok(null);
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleTag(ApiException ex) {
    HttpStatus status = TagErrorCodes.BUILTIN_CATEGORY_DELETE_FORBIDDEN.equals(ex.getErrorCode())
        ? HttpStatus.FORBIDDEN
        : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status).body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
  }
}
