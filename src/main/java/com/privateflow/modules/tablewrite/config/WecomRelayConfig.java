package com.privateflow.modules.tablewrite.config;

import java.util.ArrayList;
import java.util.List;

public final class WecomRelayConfig {

  private final String baseUrl;
  private final String keyId;
  private final String secret;

  public WecomRelayConfig(String baseUrl, String keyId, String secret) {
    this.baseUrl = normalizedBaseUrl(baseUrl);
    this.keyId = trimmed(keyId);
    this.secret = trimmed(secret);
  }

  public void requireConfigured() {
    List<String> missing = new ArrayList<>();
    require(missing, baseUrl, "WECOM_RELAY_BASE_URL");
    require(missing, keyId, "WECOM_RELAY_KEY_ID");
    require(missing, secret, "WECOM_RELAY_SECRET");
    if (!missing.isEmpty()) {
      throw new IllegalStateException("Missing required relay configuration: " + String.join(", ", missing));
    }
  }

  public String baseUrl() {
    return baseUrl;
  }

  public String keyId() {
    return keyId;
  }

  public String secret() {
    return secret;
  }

  private static void require(List<String> missing, String value, String name) {
    if (value.isBlank()) {
      missing.add(name);
    }
  }

  private static String normalizedBaseUrl(String value) {
    String normalized = trimmed(value);
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private static String trimmed(String value) {
    return value == null ? "" : value.trim();
  }
}
