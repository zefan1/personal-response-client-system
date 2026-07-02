package com.privateflow.modules.versions;

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
public class DesktopVersionController {

  private final DesktopVersionService service;

  public DesktopVersionController(DesktopVersionService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/v1/versions")
  public ApiResponse<Map<String, Object>> list(
      @RequestParam(value = "status", required = false) VersionStatus status,
      @RequestParam(value = "platform", required = false) DesktopPlatform platform,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    return ApiResponse.ok(service.list(status, platform, page, size));
  }

  @PostMapping("/admin/api/v1/versions")
  public ApiResponse<DesktopVersion> create(@RequestBody DesktopVersionCreateRequest request) {
    return ApiResponse.ok(service.create(request));
  }

  @PostMapping(value = "/admin/api/v1/versions", consumes = "multipart/form-data")
  public ApiResponse<DesktopVersion> createMultipart(
      @RequestParam("version") String version,
      @RequestParam("platform") DesktopPlatform platform,
      @RequestParam(value = "file", required = false) MultipartFile file,
      @RequestParam(value = "downloadUrl", required = false) String downloadUrl,
      @RequestParam("changelog") String changelog,
      @RequestParam(value = "updateStrategy", required = false) UpdateStrategy updateStrategy,
      @RequestParam(value = "gradualPercent", required = false) Integer gradualPercent) {
    return ApiResponse.ok(service.createWithOptionalFile(version, platform, file, downloadUrl, changelog, updateStrategy, gradualPercent));
  }

  @PutMapping("/admin/api/v1/versions/{id}")
  public ApiResponse<DesktopVersion> update(@PathVariable("id") long id, @RequestBody DesktopVersionUpdateRequest request) {
    return ApiResponse.ok(service.update(id, request));
  }

  @PutMapping("/admin/api/v1/versions/{id}/publish")
  public ApiResponse<DesktopVersion> publish(@PathVariable("id") long id) {
    return ApiResponse.ok(service.publish(id));
  }

  @PutMapping("/admin/api/v1/versions/{id}/revoke")
  public ApiResponse<DesktopVersion> revoke(@PathVariable("id") long id, @RequestBody VersionRevokeRequest request) {
    return ApiResponse.ok(service.revoke(id, request));
  }

  @DeleteMapping("/admin/api/v1/versions/{id}")
  public ApiResponse<Void> delete(@PathVariable("id") long id) {
    service.delete(id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/admin/api/v1/versions/upload")
  public ApiResponse<VersionUploadResponse> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam("platform") DesktopPlatform platform) {
    return ApiResponse.ok(service.upload(file, platform));
  }

  @GetMapping("/api/v1/desktop/version-check")
  public ApiResponse<Map<String, Object>> versionCheck(
      @RequestParam("platform") DesktopPlatform platform,
      @RequestParam("currentVersion") String currentVersion,
      @RequestParam("clientId") String clientId) {
    return ApiResponse.ok(service.versionCheck(platform, currentVersion, clientId));
  }

  @PostMapping("/api/v1/desktop/version-report")
  public ApiResponse<Map<String, Object>> report(@RequestBody VersionReportRequest request) {
    return ApiResponse.ok(service.report(request));
  }
}
