package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.privateflow.modules.tablewrite.config.WecomRelayConfig;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class WecomRelayClient {

  private static final String RELAY_PATH = "/v1/wecom/api";

  private final ObjectMapper objectMapper;
  private final WecomRelayConfig config;
  private final WecomHttpTransport httpTransport;
  private final Clock clock;
  private final Supplier<String> nonceSupplier;
  private final Supplier<String> requestIdSupplier;

  @Autowired
  public WecomRelayClient(ObjectMapper objectMapper, WecomSmartSheetConfig smartSheetConfig) {
    this(objectMapper, smartSheetConfig.relayConfig(), new WecomUrlConnectionTransport(), Clock.systemUTC(),
        () -> UUID.randomUUID().toString(), () -> UUID.randomUUID().toString());
  }

  WecomRelayClient(
      ObjectMapper objectMapper,
      WecomRelayConfig config,
      WecomHttpTransport httpTransport,
      Clock clock,
      Supplier<String> nonceSupplier,
      Supplier<String> requestIdSupplier) {
    this.objectMapper = objectMapper;
    this.config = config;
    this.httpTransport = httpTransport;
    this.clock = clock;
    this.nonceSupplier = nonceSupplier;
    this.requestIdSupplier = requestIdSupplier;
  }

  public JsonNode post(String operation, Object payload, Duration timeout) {
    requireConfiguration(operation);
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw failure(operation, "request timeout must be positive");
    }

    String requestId = value(requestIdSupplier, operation, "request id could not be generated");
    String nonce = value(nonceSupplier, operation, "nonce could not be generated");
    String rawBody = serialize(operation, payload, requestId);
    String timestamp = Long.toString(clock.instant().getEpochSecond());
    String signature = signature(operation, timestamp, nonce, rawBody);

    WecomHttpResponse response;
    try {
      response = httpTransport.send(
          URI.create(config.baseUrl() + RELAY_PATH),
          "POST",
          Map.of(
              "Content-Type", "application/json",
              "X-Relay-Key-Id", config.keyId(),
              "X-Relay-Timestamp", timestamp,
              "X-Relay-Nonce", nonce,
              "X-Relay-Request-Id", requestId,
              "X-Relay-Signature", signature),
          rawBody.getBytes(StandardCharsets.UTF_8),
          timeout);
    } catch (IOException ex) {
      throw failure(operation, "relay network request failed");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw failure(operation, "relay request interrupted");
    } catch (RuntimeException ex) {
      throw failure(operation, "relay request configuration was invalid");
    }

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw failure(operation, "relay HTTP status " + response.statusCode());
    }
    return response(operation, response.body());
  }

  private void requireConfiguration(String operation) {
    try {
      config.requireConfigured();
    } catch (IllegalStateException ex) {
      throw failure(operation, ex.getMessage());
    } catch (RuntimeException ex) {
      throw failure(operation, "relay configuration is incomplete");
    }
  }

  private String serialize(String operation, Object payload, String requestId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("operation", operation);
    body.put("payload", payload);
    body.put("requestId", requestId);
    try {
      return objectMapper.writeValueAsString(body);
    } catch (JsonProcessingException | RuntimeException ex) {
      throw failure(operation, "relay request body could not be serialized");
    }
  }

  private String signature(String operation, String timestamp, String nonce, String rawBody) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(config.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      String signingText = timestamp + "." + nonce + "." + rawBody;
      return java.util.HexFormat.of().formatHex(mac.doFinal(signingText.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException ex) {
      throw failure(operation, "relay signature could not be created");
    }
  }

  private JsonNode response(String operation, String body) {
    JsonNode root;
    try {
      ObjectReader reader = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
      root = reader.readTree(body);
    } catch (IOException | RuntimeException ex) {
      throw failure(operation, "relay response was not valid JSON");
    }
    if (root == null || !root.isObject()) {
      throw failure(operation, "relay response was not a JSON object");
    }
    JsonNode errcode = root.get("errcode");
    if (errcode == null || !errcode.isIntegralNumber() || !errcode.canConvertToInt()) {
      throw failure(operation, "relay response missing valid errcode");
    }
    return root;
  }

  private static String value(Supplier<String> supplier, String operation, String reason) {
    try {
      String value = supplier.get();
      if (value == null || value.isBlank()) {
        throw failure(operation, reason);
      }
      return value;
    } catch (WecomSmartSheetException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw failure(operation, reason);
    }
  }

  private static WecomSmartSheetException failure(String operation, String reason) {
    return new WecomSmartSheetException(operation, reason, null);
  }
}
