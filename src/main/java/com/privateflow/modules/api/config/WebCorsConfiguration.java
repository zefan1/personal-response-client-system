package com.privateflow.modules.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebCorsConfiguration implements WebMvcConfigurer {

  private final CorsOriginPolicy corsOriginPolicy;

  @org.springframework.beans.factory.annotation.Autowired
  public WebCorsConfiguration(CorsOriginPolicy corsOriginPolicy) {
    this.corsOriginPolicy = corsOriginPolicy;
  }

  WebCorsConfiguration() {
    this(CorsOriginPolicy.defaults());
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/v1/**")
        .allowedOrigins(corsOriginPolicy.allowedOrigins().toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("Authorization", "Content-Type")
        .allowCredentials(false)
        .maxAge(3600);
    registry.addMapping("/admin/api/v1/**")
        .allowedOrigins(corsOriginPolicy.allowedOrigins().toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("Authorization", "Content-Type")
        .allowCredentials(false)
        .maxAge(3600);
  }
}
