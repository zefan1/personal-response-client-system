package com.privateflow.modules.tablewrite.callback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class WecomInboundCallbackRelayClient {

  private static final TypeReference<Map<String, List<RelayEvent>>> CLAIM_RESPONSE = new TypeReference<>() {};
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private final WecomInboundCallbackRelayConfig config;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final SecureRandom secureRandom = new SecureRandom();

  @Autowired
  WecomInboundCallbackRelayClient(WecomInboundCallbackRelayConfig config, ObjectMapper objectMapper) {
    this(config, objectMapper, HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
  }

  WecomInboundCallbackRelayClient(
      WecomInboundCallbackRelayConfig config, ObjectMapper objectMapper, HttpClient httpClient) {
    this.config = config;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  List<RelayEvent> claim(int limit) {
    try {
      Map<String, List<RelayEvent>> response = post(
          "/wecom/smartsheet/internal/v1/events/claim", Map.of("limit", Math.max(1, Math.min(100, limit))), CLAIM_RESPONSE);
      return response.getOrDefault("events", List.of());
    } catch (Exception ex) {
      throw new IllegalStateException("WeCom inbound relay claim failed", ex);
    }
  }

  void acknowledge(long id, String leaseToken) {
    try {
      post("/wecom/smartsheet/internal/v1/events/ack", Map.of("id", id, "lease_token", leaseToken), new TypeReference<Map<String, Boolean>>() {});
    } catch (Exception ex) {
      throw new IllegalStateException("WeCom inbound relay acknowledgement failed", ex);
    }
  }

  private <T> T post(String path, Object value, TypeReference<T> responseType) throws Exception {
    byte[] body = objectMapper.writeValueAsBytes(value);
    String timestamp = Long.toString(Instant.now().getEpochSecond());
    byte[] nonceBytes = new byte[18];
    secureRandom.nextBytes(nonceBytes);
    String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
    String signature = signature(timestamp, nonce, "POST", path, body);
    HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
        .timeout(TIMEOUT)
        .header("Content-Type", "application/json")
        .header("X-Relay-Key-Id", config.clientId())
        .header("X-Relay-Timestamp", timestamp)
        .header("X-Relay-Nonce", nonce)
        .header("X-Relay-Signature", signature)
        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
        .build();
    HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    if (response.statusCode() != 200) {
      throw new IllegalStateException("relay returned HTTP " + response.statusCode());
    }
    return objectMapper.readValue(response.body(), responseType);
  }

  private String signature(String timestamp, String nonce, String method, String path, byte[] body) throws Exception {
    String digest = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(config.clientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return java.util.HexFormat.of().formatHex(mac.doFinal(
        String.join("\n", timestamp, nonce, method, path, digest).getBytes(StandardCharsets.UTF_8)));
  }

  record RelayEvent(
      long id,
      String event_key,
      String document_id,
      String sheet_id,
      String change_type,
      List<String> record_ids,
      String operator_name,
      String lease_token) {
  }
}
