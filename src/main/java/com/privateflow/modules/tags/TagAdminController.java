package com.privateflow.modules.tags;

import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.match.ApiResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
public class TagAdminController {

  private final TagAdminService service;

  public TagAdminController(TagAdminService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/v1/tags/categories")
  public ApiResponse<TagCategoryPage> listCategories(
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "enabled", required = false) Boolean enabled,
      @RequestParam(value = "merged", required = false) Boolean merged,
      @RequestParam(value = "selectionMode", required = false) TagSelectionMode selectionMode,
      @RequestParam(value = "builtin", required = false) Boolean builtin,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size,
      @RequestParam(value = "sortBy", defaultValue = "sortOrder") String sortBy,
      @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection) {
    return ApiResponse.ok(service.searchCategories(
        keyword, enabled, merged, selectionMode, builtin, page, size, sortBy, sortDirection));
  }

  @GetMapping("/admin/api/v1/tags/categories/{id}")
  public ApiResponse<TagCategory> categoryDetail(@PathVariable("id") long id) {
    return ApiResponse.ok(service.categoryDetail(id));
  }

  @PostMapping("/admin/api/v1/tags/categories")
  public ApiResponse<TagCategory> createCategory(@RequestBody TagCategoryRequest request) {
    return ApiResponse.ok(service.createCategory(request));
  }

  @PutMapping("/admin/api/v1/tags/categories/{id}")
  public ApiResponse<TagCategory> updateCategory(@PathVariable("id") long id, @RequestBody TagCategoryRequest request) {
    requireVersion(request == null ? null : request.version());
    return ApiResponse.ok(service.updateCategory(id, request));
  }

  @PutMapping("/admin/api/v1/tags/categories/{id}/toggle")
  public ApiResponse<TagCategory> toggleCategory(@PathVariable("id") long id, @RequestBody TagToggleRequest request) {
    requireToggle(request);
    return ApiResponse.ok(service.toggleCategory(id, Boolean.TRUE.equals(request.enabled()), request.version()));
  }

  @DeleteMapping("/admin/api/v1/tags/categories/{id}")
  public ApiResponse<Void> deleteCategory(@PathVariable("id") long id) {
    service.deleteCategory(id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/admin/api/v1/tags/categories/{id}/merge-preview")
  public ApiResponse<TagMergePreview> previewCategoryMerge(
      @PathVariable("id") long id,
      @RequestBody TagMergeRequest request) {
    requireMergeVersions(request);
    return ApiResponse.ok(service.previewCategoryMerge(id, request));
  }

  @PostMapping("/admin/api/v1/tags/categories/{id}/merge")
  public ApiResponse<TagCategory> mergeCategory(
      @PathVariable("id") long id,
      @RequestBody TagMergeRequest request) {
    requireMergeVersions(request);
    return ApiResponse.ok(service.mergeCategory(id, request));
  }

  @GetMapping("/admin/api/v1/tags/categories/export")
  public ResponseEntity<byte[]> exportCategories(
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "enabled", required = false) Boolean enabled,
      @RequestParam(value = "merged", required = false) Boolean merged,
      @RequestParam(value = "selectionMode", required = false) TagSelectionMode selectionMode,
      @RequestParam(value = "builtin", required = false) Boolean builtin,
      @RequestParam(value = "sortBy", defaultValue = "sortOrder") String sortBy,
      @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection) {
    return csv("tag-categories.csv", service.exportCategories(
        keyword, enabled, merged, selectionMode, builtin, sortBy, sortDirection));
  }

  @GetMapping("/admin/api/v1/tags/values")
  public ApiResponse<TagValuePage> listValues(
      @RequestParam(value = "categoryId", required = false) Long categoryId,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "enabled", required = false) Boolean enabled,
      @RequestParam(value = "merged", required = false) Boolean merged,
      @RequestParam(value = "systemSelectable", required = false) Boolean systemSelectable,
      @RequestParam(value = "manualSelectable", required = false) Boolean manualSelectable,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size,
      @RequestParam(value = "sortBy", defaultValue = "sortOrder") String sortBy,
      @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection) {
    return ApiResponse.ok(service.searchValues(
        categoryId, keyword, enabled, merged, systemSelectable, manualSelectable,
        page, size, sortBy, sortDirection));
  }

  @GetMapping("/admin/api/v1/tags/values/{id}")
  public ApiResponse<TagValue> valueDetail(@PathVariable("id") long id) {
    return ApiResponse.ok(service.valueDetail(id));
  }

  @PostMapping("/admin/api/v1/tags/values")
  public ApiResponse<TagValue> createValue(@RequestBody TagValueRequest request) {
    return ApiResponse.ok(service.createValue(request));
  }

  @PutMapping("/admin/api/v1/tags/values/{id}")
  public ApiResponse<TagValue> updateValue(@PathVariable("id") long id, @RequestBody TagValueRequest request) {
    requireVersion(request == null ? null : request.version());
    return ApiResponse.ok(service.updateValue(id, request));
  }

  @PutMapping("/admin/api/v1/tags/values/{id}/toggle")
  public ApiResponse<TagValue> toggleValue(@PathVariable("id") long id, @RequestBody TagToggleRequest request) {
    requireToggle(request);
    return ApiResponse.ok(service.toggleValue(id, Boolean.TRUE.equals(request.enabled()), request.version()));
  }

  @DeleteMapping("/admin/api/v1/tags/values/{id}")
  public ApiResponse<Void> deleteValue(@PathVariable("id") long id) {
    service.deleteValue(id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/admin/api/v1/tags/values/{id}/merge-preview")
  public ApiResponse<TagMergePreview> previewValueMerge(
      @PathVariable("id") long id,
      @RequestBody TagMergeRequest request) {
    requireMergeVersions(request);
    return ApiResponse.ok(service.previewValueMerge(id, request));
  }

  @PostMapping("/admin/api/v1/tags/values/{id}/merge")
  public ApiResponse<TagValue> mergeValue(
      @PathVariable("id") long id,
      @RequestBody TagMergeRequest request) {
    requireMergeVersions(request);
    return ApiResponse.ok(service.mergeValue(id, request));
  }

  @GetMapping("/admin/api/v1/tags/values/export")
  public ResponseEntity<byte[]> exportValues(
      @RequestParam(value = "categoryId", required = false) Long categoryId,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "enabled", required = false) Boolean enabled,
      @RequestParam(value = "merged", required = false) Boolean merged,
      @RequestParam(value = "systemSelectable", required = false) Boolean systemSelectable,
      @RequestParam(value = "manualSelectable", required = false) Boolean manualSelectable,
      @RequestParam(value = "sortBy", defaultValue = "sortOrder") String sortBy,
      @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection) {
    return csv("tag-values.csv", service.exportValues(
        categoryId, keyword, enabled, merged, systemSelectable, manualSelectable, sortBy, sortDirection));
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleTag(ApiException ex) {
    HttpStatus status;
    if (TagErrorCodes.BUILTIN_CATEGORY_DELETE_FORBIDDEN.equals(ex.getErrorCode())) {
      status = HttpStatus.FORBIDDEN;
    } else if (TagErrorCodes.VERSION_CONFLICT.equals(ex.getErrorCode())) {
      status = HttpStatus.CONFLICT;
    } else if (TagErrorCodes.CATEGORY_NOT_FOUND.equals(ex.getErrorCode())
        || TagErrorCodes.VALUE_NOT_FOUND.equals(ex.getErrorCode())) {
      status = HttpStatus.NOT_FOUND;
    } else {
      status = HttpStatus.BAD_REQUEST;
    }
    return ResponseEntity.status(status).body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
  }

  private void requireToggle(TagToggleRequest request) {
    if (request == null || request.enabled() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请明确选择启用或停用");
    }
    requireVersion(request.version());
  }

  private void requireMergeVersions(TagMergeRequest request) {
    requireVersion(request == null ? null : request.sourceVersion());
    requireVersion(request == null ? null : request.targetVersion());
  }

  private void requireVersion(Integer version) {
    if (version == null) {
      throw new ApiException(TagErrorCodes.VERSION_REQUIRED, "缺少数据版本，请刷新后重试");
    }
  }

  private ResponseEntity<byte[]> csv(String filename, byte[] content) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
    headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
    return new ResponseEntity<>(content, headers, HttpStatus.OK);
  }
}
