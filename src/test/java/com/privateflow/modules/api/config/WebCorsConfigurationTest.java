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
    assertThat(mappings.get("/api/v1/**").checkOrigin("https://sy.xn--15tq51d.top"))
        .isEqualTo("https://sy.xn--15tq51d.top");
    assertThat(mappings.get("/admin/api/v1/**").checkOrigin("https://sy.xn--15tq51d.top"))
        .isEqualTo("https://sy.xn--15tq51d.top");
    assertThat(mappings.get("/api/v1/**").checkOrigin("https://untrusted.example")).isNull();
  }

  @Test
  void allowsTheConfiguredProductionOrigin() {
    ExposedCorsRegistry registry = new ExposedCorsRegistry();
    new WebCorsConfiguration(new CorsOriginPolicy("http://39.108.221.95:18080"))
        .addCorsMappings(registry);

    assertThat(mappings(registry).get("/admin/api/v1/**").checkOrigin("http://39.108.221.95:18080"))
        .isEqualTo("http://39.108.221.95:18080");
  }

  private Map<String, CorsConfiguration> mappings(ExposedCorsRegistry registry) {
    return registry.configurations();
  }

  private static final class ExposedCorsRegistry extends CorsRegistry {
    Map<String, CorsConfiguration> configurations() {
      return getCorsConfigurations();
    }
  }
}
