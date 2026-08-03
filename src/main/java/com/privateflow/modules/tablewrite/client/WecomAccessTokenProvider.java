package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WecomAccessTokenProvider {

  private static final String OPERATION = "gettoken";
  private static final Duration EARLY_REFRESH = Duration.ofMinutes(5);
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_NETWORK_ATTEMPTS = 4;

  private final ObjectMapper objectMapper;
  private final WecomSmartSheetConfig config;
  private final WecomHttpTransport httpTransport;
  private final Clock clock;
  private final LongSupplier ticker;
  private final AtomicReference<Token> cachedToken = new AtomicReference<>();
  private final ReentrantLock refreshLock = new ReentrantLock();

  @Autowired
  public WecomAccessTokenProvider(ObjectMapper objectMapper, WecomSmartSheetConfig config) {
    this(objectMapper, config, new WecomUrlConnectionTransport(), Clock.systemUTC(), System::nanoTime);
  }

  static HttpClient defaultHttpClient() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  WecomAccessTokenProvider(
      ObjectMapper objectMapper,
      WecomSmartSheetConfig config,
      HttpClient httpClient,
      Clock clock) {
    this(objectMapper, config, WecomHttpTransport.from(httpClient), clock, System::nanoTime);
  }

  WecomAccessTokenProvider(
      ObjectMapper objectMapper,
      WecomSmartSheetConfig config,
      HttpClient httpClient,
      Clock clock,
      LongSupplier ticker) {
    this(objectMapper, config, WecomHttpTransport.from(httpClient), clock, ticker);
  }

  private WecomAccessTokenProvider(
      ObjectMapper objectMapper,
      WecomSmartSheetConfig config,
      WecomHttpTransport httpTransport,
      Clock clock,
      LongSupplier ticker) {
    this.objectMapper = objectMapper;
    this.config = config;
    this.httpTransport = httpTransport;
    this.clock = clock;
    this.ticker = ticker;
  }

  public String get() {
    return get(DEFAULT_TIMEOUT);
  }

  public String get(Duration timeout) {
    WecomRequestDeadline deadline = WecomRequestDeadline.start(timeout, OPERATION, ticker);
    Token current = cachedToken.get();
    if (isUsable(current)) {
      return current.value();
    }
    boolean locked = false;
    try {
      locked = tryRefreshLock(deadline);
      deadline.remaining();
      current = cachedToken.get();
      if (isUsable(current)) {
        return current.value();
      }
      Token refreshed = requestToken(deadline);
      deadline.remaining();
      cachedToken.set(refreshed);
      return refreshed.value();
    } finally {
      if (locked) {
        refreshLock.unlock();
      }
    }
  }

  public void invalidate(String rejectedToken) {
    Token current = cachedToken.get();
    while (current != null && current.value().equals(rejectedToken)) {
      if (cachedToken.compareAndSet(current, null)) {
        return;
      }
      current = cachedToken.get();
    }
  }

  private boolean tryRefreshLock(WecomRequestDeadline deadline) {
    try {
      if (!refreshLock.tryLock(deadline.remaining().toNanos(), TimeUnit.NANOSECONDS)) {
        throw new WecomSmartSheetException(OPERATION, "token refresh coordination timed out", null);
      }
      return true;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new WecomSmartSheetException(OPERATION, "token refresh coordination was interrupted", ex);
    }
  }

  private Token requestToken(WecomRequestDeadline deadline) {
    config.requireApplicationCredentials();
    WecomHttpResponse response = null;
    for (int attempt = 1; attempt <= MAX_NETWORK_ATTEMPTS; attempt++) {
      try {
        response = httpTransport.send(
            tokenUri(), "GET", Map.of(), new byte[0], deadline.remaining());
        break;
      } catch (IOException ex) {
        if (attempt == MAX_NETWORK_ATTEMPTS) {
          throw new WecomSmartSheetException(OPERATION, "network request failed", ex);
        }
        deadline.remaining();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new WecomSmartSheetException(OPERATION, "request interrupted", ex);
      } catch (IllegalArgumentException ex) {
        throw new WecomSmartSheetException(OPERATION, "request configuration was invalid", ex);
      }
    }
    if (response == null) {
      throw new WecomSmartSheetException(OPERATION, "network request failed", null);
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new WecomSmartSheetException(OPERATION, "HTTP status " + response.statusCode(), null);
    }

    JsonNode root;
    try {
      ObjectReader reader = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
      root = reader.readTree(response.body());
    } catch (IOException | RuntimeException ex) {
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
      throw new WecomSmartSheetException(OPERATION, errcode, "remote API returned an error");
    }

    JsonNode tokenNode = root.get("access_token");
    if (tokenNode == null || !tokenNode.isTextual() || tokenNode.textValue().trim().isBlank()) {
      throw new WecomSmartSheetException(OPERATION, "response missing access token", null);
    }
    String token = tokenNode.textValue().trim();
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

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private record Token(String value, Instant refreshAt) {}
}
