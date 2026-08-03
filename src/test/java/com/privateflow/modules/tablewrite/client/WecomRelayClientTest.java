package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.tablewrite.config.WecomRelayConfig;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WecomRelayClientTest {

  @Test
  void signsTheExactSerializedRequestBody() throws Exception {
    CapturedRequest captured = new CapturedRequest();
    WecomRelayClient client = new WecomRelayClient(
        new ObjectMapper(),
        new WecomRelayConfig("https://relay.example/", "local-test", "relay-secret"),
        (uri, method, headers, body, timeout) -> {
          captured.uri = uri.toString();
          captured.method = method;
          captured.headers = headers;
          captured.body = new String(body, StandardCharsets.UTF_8);
          captured.timeout = timeout;
          return new WecomHttpResponse(200, "{\"errcode\":0}");
        },
        Clock.fixed(Instant.ofEpochSecond(1_725_000_000L), ZoneOffset.UTC),
        () -> "nonce-1",
        () -> "request-1");

    JsonNode result = client.post("get_records", Map.of("document_id", "doc-1"), Duration.ofSeconds(5));

    assertThat(captured.uri).isEqualTo("https://relay.example/v1/wecom/api");
    assertThat(captured.method).isEqualTo("POST");
    assertThat(captured.timeout).isEqualTo(Duration.ofSeconds(5));
    assertThat(captured.headers).containsEntry("X-Relay-Key-Id", "local-test")
        .containsEntry("X-Relay-Timestamp", "1725000000")
        .containsEntry("X-Relay-Nonce", "nonce-1")
        .containsEntry("X-Relay-Request-Id", "request-1")
        .containsEntry("X-Relay-Signature", expectedSignature("relay-secret", "1725000000", "nonce-1", captured.body));
    assertThat(new ObjectMapper().readTree(captured.body)).isEqualTo(new ObjectMapper().readTree(
        "{\"operation\":\"get_records\",\"payload\":{\"document_id\":\"doc-1\"},\"requestId\":\"request-1\"}"));
    assertThat(result.path("errcode").asInt()).isZero();
  }

  @Test
  void rejectsMissingRelaySettingsBeforeSending() {
    AtomicInteger calls = new AtomicInteger();
    WecomRelayClient client = new WecomRelayClient(
        new ObjectMapper(),
        new WecomRelayConfig("", "local-test", "relay-secret"),
        (uri, method, headers, body, timeout) -> {
          calls.incrementAndGet();
          return new WecomHttpResponse(200, "{\"errcode\":0}");
        },
        Clock.systemUTC(),
        () -> "nonce-1",
        () -> "request-1");

    assertThatThrownBy(() -> client.post("get_records", Map.of(), Duration.ofSeconds(5)))
        .isInstanceOf(WecomSmartSheetException.class)
        .hasMessageContaining("WECOM_RELAY_BASE_URL");
    assertThat(calls).hasValue(0);
  }

  private static String expectedSignature(String secret, String timestamp, String nonce, String rawBody) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return java.util.HexFormat.of().formatHex(mac.doFinal(
          (timestamp + "." + nonce + "." + rawBody).getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static final class CapturedRequest {
    private String uri;
    private String method;
    private Map<String, String> headers;
    private String body;
    private Duration timeout;
  }
}
