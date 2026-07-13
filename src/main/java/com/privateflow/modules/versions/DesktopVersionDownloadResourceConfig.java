package com.privateflow.modules.versions;

import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DesktopVersionDownloadResourceConfig implements WebMvcConfigurer {

  private final Path storageRoot;
  private final SystemConfigRepository configRepository;

  public DesktopVersionDownloadResourceConfig(
      SystemConfigRepository configRepository,
      @Value("${version.storage.root:${VERSION_STORAGE_ROOT:uploads/desktop-releases}}") String storageRoot) {
    this.configRepository = configRepository;
    this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/downloads/desktop-releases/**")
        .addResourceLocations(configRepository.findValue("version.storage.root")
            .filter(value -> !value.isBlank())
            .map(value -> Path.of(value).toAbsolutePath().normalize())
            .orElse(storageRoot)
            .toUri().toString() + "/");
  }
}
