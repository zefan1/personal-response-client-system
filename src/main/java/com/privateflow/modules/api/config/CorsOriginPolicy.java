package com.privateflow.modules.api.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Keeps MVC CORS processing and authentication error responses on one policy. */
@Component
public class CorsOriginPolicy {

  private static final Set<String> DEFAULT_ORIGINS = Set.of(
      "file://",
      "http://localhost:5173",
      "http://127.0.0.1:5173",
      "http://localhost:5174",
      "http://127.0.0.1:5174",
      "http://localhost:5175",
      "http://127.0.0.1:5175",
      "http://localhost:4173",
      "http://127.0.0.1:4173",
      "https://sy.xn--15tq51d.top");

  private final Set<String> allowedOrigins;

  public CorsOriginPolicy(
      @Value("${APP_CORS_ALLOWED_ORIGINS:}") String configuredOrigins) {
    LinkedHashSet<String> origins = new LinkedHashSet<>(DEFAULT_ORIGINS);
    if (configuredOrigins != null) {
      Arrays.stream(configuredOrigins.split(","))
          .map(String::trim)
          .filter(origin -> !origin.isBlank())
          .forEach(origins::add);
    }
    allowedOrigins = Set.copyOf(origins);
  }

  public static CorsOriginPolicy defaults() {
    return new CorsOriginPolicy("");
  }

  public Set<String> allowedOrigins() {
    return allowedOrigins;
  }

  public boolean allows(String origin) {
    return origin != null && allowedOrigins.contains(origin);
  }
}
