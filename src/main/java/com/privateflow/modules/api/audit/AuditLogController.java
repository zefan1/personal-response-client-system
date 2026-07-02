package com.privateflow.modules.api.audit;

import com.privateflow.modules.match.ApiResponse;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditLogController {

  private final AuditLogService service;

  public AuditLogController(AuditLogService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/v1/audit-logs")
  public ApiResponse<Map<String, Object>> list(
      @RequestParam(value = "action", required = false) String action,
      @RequestParam(value = "operator", required = false) String operator,
      @RequestParam(value = "targetType", required = false) String targetType,
      @RequestParam(value = "targetId", required = false) String targetId,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    return ApiResponse.ok(service.list(query(action, operator, targetType, targetId, keyword, startDate, endDate, page, size)));
  }

  @GetMapping("/admin/api/v1/audit-logs/actions")
  public ApiResponse<Map<String, Object>> actions() {
    return ApiResponse.ok(service.actions());
  }

  @PostMapping("/admin/api/v1/audit-logs/export")
  public ApiResponse<Map<String, Object>> export(@RequestBody(required = false) AuditExportRequest request) {
    AuditExportRequest actual = request == null ? new AuditExportRequest(null, null, null, null, null, null, null) : request;
    return ApiResponse.ok(service.export(query(
        actual.action(),
        actual.operator(),
        actual.targetType(),
        actual.targetId(),
        actual.keyword(),
        actual.startDate(),
        actual.endDate(),
        1,
        20)));
  }

  @GetMapping("/admin/api/v1/audit-logs/export/{exportId}")
  public ApiResponse<Map<String, Object>> exportStatus(@PathVariable("exportId") String exportId) {
    return ApiResponse.ok(service.exportStatus(exportId));
  }

  @GetMapping("/admin/api/v1/audit-logs/export/{exportId}/download")
  public ResponseEntity<String> download(@PathVariable("exportId") String exportId) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exportId + ".csv\"")
        .contentType(new MediaType("text", "csv"))
        .body(service.downloadCsv(exportId));
  }

  private AuditLogQuery query(
      String action,
      String operator,
      String targetType,
      String targetId,
      String keyword,
      LocalDate startDate,
      LocalDate endDate,
      int page,
      int size) {
    return new AuditLogQuery(actions(action), operator, targetType, targetId, keyword, startDate, endDate, page, size);
  }

  private List<String> actions(String action) {
    if (action == null || action.isBlank()) {
      return List.of();
    }
    return Arrays.stream(action.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
  }
}
