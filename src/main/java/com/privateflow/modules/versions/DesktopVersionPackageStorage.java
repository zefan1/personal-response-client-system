package com.privateflow.modules.versions;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DesktopVersionPackageStorage {

  private final Path storageRoot;
  private final String publicBaseUrl;
  private final SystemConfigRepository configRepository;

  public DesktopVersionPackageStorage(
      SystemConfigRepository configRepository,
      @Value("${version.storage.root:${VERSION_STORAGE_ROOT:uploads/desktop-releases}}") String storageRoot,
      @Value("${version.storage.public-base-url:${VERSION_PUBLIC_BASE_URL:/downloads/desktop-releases}}") String publicBaseUrl) {
    this.configRepository = configRepository;
    this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    this.publicBaseUrl = trimTrailingSlash(publicBaseUrl, "/downloads/desktop-releases");
  }

  public VersionUploadResponse store(MultipartFile file, DesktopPlatform platform) {
    LocalDate today = LocalDate.now();
    String original = file.getOriginalFilename() == null ? "installer" : file.getOriginalFilename();
    String extension = extension(original);
    String safeName = sanitize(original);
    String fileName = platform.name() + "-" + UUID.randomUUID() + "-" + safeName;
    Path root = storageRoot();
    Path directory = root.resolve(today.toString()).normalize();
    Path target = directory.resolve(fileName).normalize();
    if (!target.startsWith(root)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "version storage path invalid");
    }
    try (InputStream input = file.getInputStream()) {
      Files.createDirectories(directory);
      Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
      return new VersionUploadResponse(publicBaseUrl() + "/" + today + "/" + fileName, Files.size(target));
    } catch (IOException ex) {
      throw new ApiException(ApiErrorCodes.VERSION_UPLOAD_FAILED, "installer upload failed");
    }
  }

  private Path storageRoot() {
    return configRepository.findValue("version.storage.root")
        .filter(value -> !value.isBlank())
        .map(value -> Path.of(value).toAbsolutePath().normalize())
        .orElse(storageRoot);
  }

  private String publicBaseUrl() {
    return configRepository.findValue("version.storage.public_base_url")
        .filter(value -> !value.isBlank())
        .map(value -> trimTrailingSlash(value, "/downloads/desktop-releases"))
        .orElse(publicBaseUrl);
  }

  private String sanitize(String value) {
    String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "_");
    if (!sanitized.isBlank()) {
      return sanitized;
    }
    String extension = extension(value);
    return extension.isBlank() ? "installer" : "installer." + extension;
  }

  private String extension(String filename) {
    if (filename == null || !filename.contains(".")) {
      return "";
    }
    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
  }

  private String trimTrailingSlash(String value, String fallback) {
    String normalized = value == null || value.isBlank() ? fallback : value.trim();
    while (normalized.endsWith("/") && normalized.length() > 1) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
