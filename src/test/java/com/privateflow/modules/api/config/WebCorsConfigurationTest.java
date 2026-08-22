package com.privateflow.modules.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class WebCorsConfigurationTest {

  @Test
  void allowsThePackagedDesktopClientButNotUnknownOrigins() {
    ExposedCorsRegistry registry = new ExposedCorsRegistry();
    new WebCorsConfiguration().addCorsMappings(registry);

    Map<String, CorsConfiguration> mappings = registry.configurations();
    assertThat(mappings.get("/api/v1/**").checkOrigin("file://")).isEqualTo("file://");
    assertThat(mappings.get("/admin/api/v1/**").checkOrigin("file://")).isEqualTo("file://");
    assertThat(mappings.get("/api/v1/**").checkOrigin("https://untrusted.example")).isNull();
  }

  private static final class ExposedCorsRegistry extends CorsRegistry {
    Map<String, CorsConfiguration> configurations() {
      return getCorsConfigurations();
    }
  }
}
