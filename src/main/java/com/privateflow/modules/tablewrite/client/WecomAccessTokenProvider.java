package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class WecomAccessTokenProvider {

  private static final String OPERATION = "gettoken";
  private static final Duration EARLY_REFRESH = Duration.ofMinutes(5);

  private final ObjectMapper objectMapper;
  private final WecomSmartSheetConfig config;
  private final HttpClient httpClient;
  private final Clock clock;
  private volatile Token cachedToken;

  public WecomAccessTokenProvider(ObjectMapper objectMapper, WecomSmartSheetConfig config) {
    this(objectMapper, config, HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build(), Clock.systemUTC());
  }

  WecomAccessTokenProvider(
      ObjectMapper objectMapper,
      WecomSmartSheetConfig config,
      HttpClient httpClient,
      Clock clock) {
    this.objectMapper = objectMapper;
    this.config = config;
    this.httpClient = httpClient;
    this.clock = clock;
  }

  public String get() {
    Token current = cachedToken;
    if (isUsable(current)) {
      return current.value();
    }
    synchronized (this) {
      current = cachedToken;
      if (isUsable(current)) {
        return current.value();
      }
      Token refreshed = requestToken();
      cachedToken = refreshed;
      return refreshed.value();
    }
  }

  public synchronized void invalidate(String rejectedToken) {
    if (cachedToken != null && cachedToken.value().equals(rejectedToken)) {
      cachedToken = null;
    }
  }

  private Token requestToken() {
    config.requireConfigured();
    HttpResponse<String> response;
    try {
      response = httpClient.send(HttpRequest.newBuilder()
          .uri(tokenUri())
          .GET()
          .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException ex) {
      throw new WecomSmartSheetException(OPERATION, "network request failed", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new WecomSmartSheetException(OPERATION, "request interrupted", ex);
    } catch (IllegalArgumentException ex) {
      throw new WecomSmartSheetException(OPERATION, "request configuration was invalid", ex);
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new WecomSmartSheetException(OPERATION, "HTTP status " + response.statusCode(), null);
    }

    JsonNode root;
    try {
      root = objectMapper.readTree(response.body());
    } catch (IOException ex) {
      throw new WecomSmartSheetException(OPERATION, "response was not valid JSON", ex);
    }
    if (root == null || !root.isObject()) {
      throw new WecomSmartSheetException(OPERATION, "response was not a JSON object", null);
    }

    JsonNode errcodeNode = root.get("errcode");
    if (errcodeNode == null || !errcodeNode.isIntegralNumber() || !errcodeNode.canConvertToInt()) {
      throw new WecomSmartSheetException(OPERATION, "response missing valid errcode", null);
    }
    int errcode = errcodeNode.intValue();
    if (errcode != 0) {
      throw new WecomSmartSheetException(OPERATION, errcode,
          redact(root.path("errmsg").asText("")));
    }

    String token = root.path("access_token").asText("").trim();
    if (token.isBlank()) {
      throw new WecomSmartSheetException(OPERATION, "response missing access token", null);
    }
    JsonNode expiresInNode = root.get("expires_in");
    if (expiresInNode == null || !expiresInNode.isIntegralNumber() || !expiresInNode.canConvertToLong()
        || expiresInNode.longValue() <= 0) {
      throw new WecomSmartSheetException(OPERATION, "response missing positive expiry", null);
    }
    long expiresIn = expiresInNode.longValue();
    long cacheSeconds = expiresIn > EARLY_REFRESH.toSeconds()
        ? expiresIn - EARLY_REFRESH.toSeconds()
        : Math.max(1, expiresIn / 2);
    return new Token(token, clock.instant().plusSeconds(cacheSeconds));
  }

  private URI tokenUri() {
    return URI.create(config.apiBaseUrl() + "/cgi-bin/gettoken?corpid=" + encode(config.corpId())
        + "&corpsecret=" + encode(config.appSecret()));
  }

  private boolean isUsable(Token token) {
    return token != null && clock.instant().isBefore(token.refreshAt());
  }

  private String redact(String message) {
    return message.replace(config.corpId(), "[redacted]")
        .replace(config.appSecret(), "[redacted]")
        .replaceAll("(?i)corp(?:id|secret)\\s*=\\s*[^\\s&]+", "[redacted]");
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private record Token(String value, Instant refreshAt) {}
}
