package com.privateflow.modules.quicksearch.admin;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QuickSearchImageStorage {

  private final Path storageRoot;
  private final String publicBaseUrl;
  private final SystemConfigRepository configRepository;

  public QuickSearchImageStorage(
      SystemConfigRepository configRepository,
      @Value("${quicksearch.storage.root:${QUICKSEARCH_STORAGE_ROOT:uploads/quick-search}}") String storageRoot,
      @Value("${quicksearch.storage.public-base-url:${QUICKSEARCH_PUBLIC_BASE_URL:/uploads/quick-search}}") String publicBaseUrl) {
    this.configRepository = configRepository;
    this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
  }

  public String store(byte[] bytes, String extension) {
    LocalDate today = LocalDate.now();
    String fileName = UUID.randomUUID() + "." + extension;
    Path root = configRepository.findValue("quicksearch.storage.root")
        .filter(value -> !value.isBlank())
        .map(value -> Path.of(value).toAbsolutePath().normalize())
        .orElse(storageRoot);
    Path directory = root.resolve(today.toString()).normalize();
    Path target = directory.resolve(fileName).normalize();
    if (!target.startsWith(root)) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "图片上传目录不合法");
    }
    try {
      Files.createDirectories(directory);
      Files.write(target, bytes);
      String baseUrl = configRepository.findValue("quicksearch.storage.public_base_url")
          .filter(value -> !value.isBlank())
          .map(this::trimTrailingSlash)
          .orElse(publicBaseUrl);
      return baseUrl + "/" + today + "/" + fileName;
    } catch (IOException ex) {
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "图片上传失败");
    }
  }

  private String trimTrailingSlash(String value) {
    String normalized = value == null || value.isBlank() ? "/uploads/quick-search" : value.trim();
    while (normalized.endsWith("/") && normalized.length() > 1) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
