package com.privateflow.modules.quicksearch.admin;

import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class QuickSearchUploadResourceConfig implements WebMvcConfigurer {

  private final Path storageRoot;
  private final SystemConfigRepository configRepository;

  public QuickSearchUploadResourceConfig(
      SystemConfigRepository configRepository,
      @Value("${quicksearch.storage.root:${QUICKSEARCH_STORAGE_ROOT:uploads/quick-search}}") String storageRoot) {
    this.configRepository = configRepository;
    this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/uploads/quick-search/**")
        .addResourceLocations(configRepository.findValue("quicksearch.storage.root")
            .filter(value -> !value.isBlank())
            .map(value -> Path.of(value).toAbsolutePath().normalize())
            .orElse(storageRoot)
            .toUri().toString() + "/");
  }
}
