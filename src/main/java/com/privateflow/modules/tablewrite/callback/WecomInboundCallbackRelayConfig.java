package com.privateflow.modules.tablewrite.callback;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Development bridge configuration. The permanent server deployment may use a loopback URL. */
@Component
class WecomInboundCallbackRelayConfig {

  private final String baseUrl;
  private final String clientId;
  private final String clientSecret;

  WecomInboundCallbackRelayConfig(
      @Value("${WECOM_INBOUND_RELAY_BASE_URL:}") String baseUrl,
      @Value("${WECOM_INBOUND_RELAY_CLIENT_ID:}") String clientId,
      @Value("${WECOM_INBOUND_RELAY_CLIENT_SECRET:}") String clientSecret) {
    this.baseUrl = text(baseUrl).replaceAll("/+$", "");
    this.clientId = text(clientId);
    this.clientSecret = text(clientSecret);
  }

  boolean configured() {
    return !baseUrl.isEmpty() && !clientId.isEmpty() && clientSecret.length() >= 32;
  }

  String baseUrl() {
    return baseUrl;
  }

  String clientId() {
    return clientId;
  }

  String clientSecret() {
    return clientSecret;
  }

  private static String text(String value) {
    return value == null ? "" : value.trim();
  }
}
