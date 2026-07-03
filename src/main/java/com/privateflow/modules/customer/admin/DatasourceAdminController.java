package com.privateflow.modules.customer.admin;

import com.privateflow.modules.match.ApiResponse;
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
public class DatasourceAdminController {

  private final DatasourceAdminService service;

  public DatasourceAdminController(DatasourceAdminService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/v1/datasources")
  public ApiResponse<Map<String, Object>> list() {
    return ApiResponse.ok(service.list());
  }

  @PostMapping("/admin/api/v1/datasources")
  public ApiResponse<Datasource> create(@RequestBody DatasourceRequest request) {
    return ApiResponse.ok(service.create(request));
  }

  @PutMapping("/admin/api/v1/datasources/{id}")
  public ApiResponse<Datasource> update(@PathVariable("id") long id, @RequestBody DatasourceRequest request) {
    return ApiResponse.ok(service.update(id, request));
  }

  @DeleteMapping("/admin/api/v1/datasources/{id}")
  public ApiResponse<Map<String, Object>> delete(@PathVariable("id") long id) {
    return ApiResponse.ok(service.delete(id));
  }

  @PutMapping("/admin/api/v1/datasources/{id}/toggle")
  public ApiResponse<Datasource> toggle(@PathVariable("id") long id, @RequestBody DatasourceToggleRequest request) {
    return ApiResponse.ok(service.toggle(id, request.enabled()));
  }

  @PutMapping("/admin/api/v1/datasources/{id}/replace")
  public ApiResponse<Map<String, Object>> replace(@PathVariable("id") long id, @RequestBody DatasourceReplaceRequest request) {
    return ApiResponse.ok(service.replace(id, request));
  }

  @GetMapping("/admin/api/v1/datasources/{id}/mappings")
  public ApiResponse<Map<String, Object>> mappings(@PathVariable("id") long id) {
    return ApiResponse.ok(service.mappings(id));
  }

  @PutMapping("/admin/api/v1/datasources/{id}/mappings")
  public ApiResponse<Map<String, Object>> saveMappings(@PathVariable("id") long id, @RequestBody MappingSaveRequest request) {
    return ApiResponse.ok(service.saveMappings(id, request));
  }

  @GetMapping("/admin/api/v1/datasources/{id}/mappings/versions")
  public ApiResponse<Map<String, Object>> mappingVersions(@PathVariable("id") long id) {
    return ApiResponse.ok(service.mappingVersions(id));
  }

  @PostMapping("/admin/api/v1/datasources/{id}/mappings/restore")
  public ApiResponse<Map<String, Object>> restoreMappings(@PathVariable("id") long id, @RequestBody MappingRestoreRequest request) {
    return ApiResponse.ok(service.restoreMappings(id, request));
  }

  @GetMapping("/admin/api/v1/datasources/{id}/mappings/compare")
  public ApiResponse<Map<String, Object>> compareMappings(@PathVariable("id") long id) {
    return ApiResponse.ok(service.compareMappings(id));
  }

  @GetMapping("/admin/api/v1/datasources/{id}/columns")
  public ApiResponse<Map<String, Object>> columns(@PathVariable("id") long id) {
    return ApiResponse.ok(service.columns(id));
  }

  @GetMapping("/admin/api/v1/customer-fields")
  public ApiResponse<Map<String, Object>> customerFields() {
    return ApiResponse.ok(service.customerFields());
  }

  @GetMapping("/admin/api/v1/datasources/sync-status")
  public ApiResponse<Map<String, Object>> syncStatus() {
    return ApiResponse.ok(service.syncStatus());
  }

  @PostMapping("/admin/api/v1/datasources/{id}/sync")
  public ApiResponse<Map<String, Object>> sync(@PathVariable("id") long id) {
    return ApiResponse.ok(service.sync(id));
  }

  @PostMapping("/admin/api/v1/datasources/import")
  public ApiResponse<CsvImportResult> importCsv(@RequestParam("file") MultipartFile file) {
    return ApiResponse.ok(service.importCsv(file));
  }

  @GetMapping("/admin/api/v1/datasources/import-logs")
  public ApiResponse<Map<String, Object>> importLogs() {
    return ApiResponse.ok(service.importLogs());
  }
}
