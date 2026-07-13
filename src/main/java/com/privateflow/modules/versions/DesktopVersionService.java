package com.privateflow.modules.versions;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.alert.SystemAlertRepository;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DesktopVersionService {

  private static final Pattern VERSION = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
  private final DesktopVersionRepository repository;
  private final SystemConfigRepository configRepository;
  private final SystemAlertRepository alertRepository;
  private final AuditLogger auditLogger;
  private final DesktopVersionPackageStorage packageStorage;

  public DesktopVersionService(
      DesktopVersionRepository repository,
      SystemConfigRepository configRepository,
      SystemAlertRepository alertRepository,
      AuditLogger auditLogger,
      DesktopVersionPackageStorage packageStorage) {
    this.repository = repository;
    this.configRepository = configRepository;
    this.alertRepository = alertRepository;
    this.auditLogger = auditLogger;
    this.packageStorage = packageStorage;
  }

  public Map<String, Object> list(VersionStatus status, DesktopPlatform platform, int page, int size) {
    requireAdmin();
    int safePage = Math.max(1, page);
    int safeSize = Math.max(10, Math.min(size, 50));
    long total = repository.count(status, platform);
    return Map.of(
        "total", total,
        "page", safePage,
        "size", safeSize,
        "totalPages", Math.max(1, (int) Math.ceil(total / (double) safeSize)),
        "items", repository.list(status, platform, safePage, safeSize));
  }

  @Transactional
  public DesktopVersion create(DesktopVersionCreateRequest request) {
    requireAdmin();
    validateCreate(request);
    if (repository.findByVersionAndPlatform(request.version().trim(), request.platform()).isPresent()) {
      throw new ApiException(ApiErrorCodes.VERSION_EXISTS, "version already exists for platform");
    }
    long id = repository.create(request, AuthContext.username());
    return require(id);
  }

  @Transactional
  public DesktopVersion createWithOptionalFile(
      String version,
      DesktopPlatform platform,
      MultipartFile file,
      String downloadUrl,
      String changelog,
      UpdateStrategy updateStrategy,
      Integer gradualPercent) {
    VersionUploadResponse upload = null;
    if (file != null && !file.isEmpty()) {
      upload = upload(file, platform);
    }
    return create(new DesktopVersionCreateRequest(
        version,
        platform,
        upload == null ? downloadUrl : upload.downloadUrl(),
        changelog,
        updateStrategy,
        gradualPercent,
        upload == null ? null : upload.fileSize()));
  }

  @Transactional
  public DesktopVersion update(long id, DesktopVersionUpdateRequest request) {
    requireAdmin();
    DesktopVersion existing = require(id);
    if (existing.status() == VersionStatus.PUBLISHED) {
      throw new ApiException(ApiErrorCodes.VERSION_STATUS_INVALID, "published version cannot be edited");
    }
    validateUpdate(request);
    if (request.version() != null && !request.version().isBlank()
        && !request.version().trim().equals(existing.version())
        && repository.findByVersionAndPlatform(request.version().trim(), existing.platform()).isPresent()) {
      throw new ApiException(ApiErrorCodes.VERSION_EXISTS, "version already exists for platform");
    }
    repository.update(id, request, existing);
    return require(id);
  }

  @Transactional
  public DesktopVersion publish(long id) {
    requireAdmin();
    DesktopVersion existing = require(id);
    if (existing.status() != VersionStatus.DRAFT) {
      throw new ApiException(ApiErrorCodes.VERSION_STATUS_INVALID, "only draft version can be published");
    }
    if (existing.downloadUrl() == null || existing.downloadUrl().isBlank()) {
      throw new ApiException(ApiErrorCodes.VERSION_PACKAGE_MISSING, "installer package is required before publish");
    }
    repository.publish(id);
    auditLogger.log("VERSION_PUBLISH", AuthContext.username(), "version", String.valueOf(id), existing.version());
    return require(id);
  }

  @Transactional
  public DesktopVersion revoke(long id, VersionRevokeRequest request) {
    requireAdmin();
    DesktopVersion existing = require(id);
    if (existing.status() != VersionStatus.PUBLISHED) {
      throw new ApiException(ApiErrorCodes.VERSION_STATUS_INVALID, "only published version can be revoked");
    }
    if (request == null || request.reason() == null || request.reason().isBlank()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "revoke reason is required");
    }
    if (request.reason().length() > 500) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "revoke reason max length is 500");
    }
    if (request.alternativeVersion() != null && !request.alternativeVersion().isBlank()) {
      DesktopVersion alternative = repository.findByVersionAndPlatform(request.alternativeVersion().trim(), existing.platform())
          .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "alternative version must be published"));
      if (alternative.status() != VersionStatus.PUBLISHED) {
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, "alternative version must be published");
      }
    }
    repository.revoke(id, request);
    auditLogger.log("VERSION_REVOKE", AuthContext.username(), "version", String.valueOf(id), request.reason());
    return require(id);
  }

  @Transactional
  public void delete(long id) {
    requireAdmin();
    DesktopVersion existing = require(id);
    if (existing.status() != VersionStatus.DRAFT) {
      throw new ApiException(ApiErrorCodes.VERSION_STATUS_INVALID, "only draft version can be deleted");
    }
    repository.delete(id);
  }

  public VersionUploadResponse upload(MultipartFile file, DesktopPlatform platform) {
    requireAdmin();
    if (platform == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "platform is required");
    }
    validateFile(file, platform);
    try {
      return packageStorage.store(file, platform);
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      alertRepository.activate("COS_UPLOAD_FAILED", "WARN", "desktop installer upload failed", "VERSION", ex.getMessage());
      throw new ApiException(ApiErrorCodes.VERSION_UPLOAD_FAILED, "installer upload failed");
    }
  }

  public Map<String, Object> versionCheck(DesktopPlatform platform, String currentVersion, String clientId) {
    validateDesktopCheck(platform, currentVersion, clientId);
    DesktopVersion latest = repository.latestPublished(platform).orElse(null);
    boolean hasUpdate = false;
    DesktopVersion latestVersion = null;
    if (latest != null && compareVersion(latest.version(), currentVersion.trim()) > 0 && inGradualRange(latest, clientId.trim())) {
      hasUpdate = true;
      latestVersion = latest;
    }
    DesktopVersion current = repository.findByVersionAndPlatform(currentVersion.trim(), platform).orElse(null);
    boolean revoked = current != null && current.status() == VersionStatus.REVOKED;
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("hasUpdate", hasUpdate);
    result.put("latestVersion", latestVersion);
    result.put("currentVersionRevoked", revoked);
    if (revoked) {
      result.put("revokeInfo", Map.of(
          "revokedVersion", current.version(),
          "reason", current.revokeReason() == null ? "" : current.revokeReason(),
          "alternativeVersion", current.alternativeVersion() == null ? "" : current.alternativeVersion(),
          "revokedAt", current.revokedAt() == null ? "" : current.revokedAt().toString()));
    } else {
      result.put("revokeInfo", null);
    }
    result.put("reportIntervalHours", intConfig("version.report_interval_hours", 24));
    return result;
  }

  @Transactional
  public Map<String, Object> report(VersionReportRequest request) {
    if (request == null || request.platform() == null || blank(request.clientId()) || blank(request.version())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "clientId, version and platform are required");
    }
    validateVersion(request.version());
    repository.report(request);
    return Map.of("ok", true);
  }

  private DesktopVersion require(long id) {
    return repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "version not found"));
  }

  private void requireAdmin() {
    AuthUser user = AuthContext.current();
    if (user == null || user.role() != Role.ADMIN) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "permission denied");
    }
  }

  private void validateCreate(DesktopVersionCreateRequest request) {
    if (request == null || request.platform() == null || blank(request.version()) || blank(request.changelog())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "version, platform and changelog are required");
    }
    validateVersion(request.version());
    validateStrategy(request.updateStrategy(), request.gradualPercent());
    if (blank(request.downloadUrl())) {
      throw new ApiException(ApiErrorCodes.VERSION_PACKAGE_MISSING, "file or downloadUrl is required");
    }
  }

  private void validateUpdate(DesktopVersionUpdateRequest request) {
    if (request == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "request body required");
    }
    if (!blank(request.version())) {
      validateVersion(request.version());
    }
    if (request.updateStrategy() != null) {
      validateStrategy(request.updateStrategy(), request.gradualPercent());
    }
  }

  private void validateDesktopCheck(DesktopPlatform platform, String currentVersion, String clientId) {
    if (platform == null || blank(currentVersion) || blank(clientId)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "platform, currentVersion and clientId are required");
    }
    validateVersion(currentVersion);
  }

  private void validateVersion(String version) {
    if (version == null || !VERSION.matcher(version.trim()).matches()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "version must match X.Y.Z");
    }
  }

  private void validateStrategy(UpdateStrategy strategy, Integer gradualPercent) {
    UpdateStrategy actual = strategy == null ? UpdateStrategy.OPTIONAL : strategy;
    if (actual == UpdateStrategy.GRADUAL && (gradualPercent == null || gradualPercent < 1 || gradualPercent > 99)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "gradualPercent range is 1-99");
    }
  }

  private void validateFile(MultipartFile file, DesktopPlatform platform) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "file is required");
    }
    long maxBytes = (long) intConfig("version.max_file_size_mb", 500) * 1024L * 1024L;
    if (file.getSize() > maxBytes) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "installer file exceeds max size");
    }
    String ext = extension(file.getOriginalFilename());
    if (platform == DesktopPlatform.WINDOWS && !"exe".equals(ext)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "windows installer must be .exe");
    }
    if (platform == DesktopPlatform.MAC && !"dmg".equals(ext)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "mac installer must be .dmg");
    }
  }

  private int compareVersion(String left, String right) {
    int[] l = parseVersion(left);
    int[] r = parseVersion(right);
    for (int i = 0; i < 3; i++) {
      int compared = Integer.compare(l[i], r[i]);
      if (compared != 0) {
        return compared;
      }
    }
    return 0;
  }

  private int[] parseVersion(String version) {
    String[] parts = version.trim().split("\\.");
    return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
  }

  private boolean inGradualRange(DesktopVersion version, String clientId) {
    if (version.updateStrategy() != UpdateStrategy.GRADUAL) {
      return true;
    }
    int percent = version.gradualPercent() == null ? 0 : version.gradualPercent();
    return murmurBucket(clientId) < percent;
  }

  private int murmurBucket(String clientId) {
    byte[] data = clientId.getBytes(StandardCharsets.UTF_8);
    int seed = 0;
    int hash = seed;
    int roundedEnd = data.length & 0xfffffffc;
    for (int i = 0; i < roundedEnd; i += 4) {
      int k = (data[i] & 0xff)
          | ((data[i + 1] & 0xff) << 8)
          | ((data[i + 2] & 0xff) << 16)
          | (data[i + 3] << 24);
      k *= 0xcc9e2d51;
      k = Integer.rotateLeft(k, 15);
      k *= 0x1b873593;
      hash ^= k;
      hash = Integer.rotateLeft(hash, 13);
      hash = hash * 5 + 0xe6546b64;
    }
    int k1 = 0;
    switch (data.length & 3) {
      case 3:
        k1 ^= (data[roundedEnd + 2] & 0xff) << 16;
      case 2:
        k1 ^= (data[roundedEnd + 1] & 0xff) << 8;
      case 1:
        k1 ^= data[roundedEnd] & 0xff;
        k1 *= 0xcc9e2d51;
        k1 = Integer.rotateLeft(k1, 15);
        k1 *= 0x1b873593;
        hash ^= k1;
      default:
        break;
    }
    hash ^= data.length;
    hash ^= hash >>> 16;
    hash *= 0x85ebca6b;
    hash ^= hash >>> 13;
    hash *= 0xc2b2ae35;
    hash ^= hash >>> 16;
    return Math.floorMod(hash, 100);
  }

  private int intConfig(String key, int fallback) {
    return configRepository.findValue(key).map(value -> {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException ex) {
        return fallback;
      }
    }).orElse(fallback);
  }

  private String extension(String filename) {
    if (filename == null || !filename.contains(".")) {
      return "";
    }
    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
