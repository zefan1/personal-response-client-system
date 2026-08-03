package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import com.privateflow.modules.tablewrite.config.WecomTransportMode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WecomSmartSheetApiClient {

  private static final String SAFE_OPERATION = "request";
  private static final Map<String, String> PATHS = Map.of(
      "get_fields", "/cgi-bin/wedoc/smartsheet/get_fields",
      "get_records", "/cgi-bin/wedoc/smartsheet/get_records",
      "add_records", "/cgi-bin/wedoc/smartsheet/add_records",
      "update_records", "/cgi-bin/wedoc/smartsheet/update_records",
      "create_doc", "/cgi-bin/wedoc/create_doc",
      "get_sheet", "/cgi-bin/wedoc/smartsheet/get_sheet",
      "get_views", "/cgi-bin/wedoc/smartsheet/get_views",
      "update_fields", "/cgi-bin/wedoc/smartsheet/update_fields",
      "add_fields", "/cgi-bin/wedoc/smartsheet/add_fields");

  private final ObjectMapper objectMapper;
  private final WecomSmartSheetConfig config;
  private final WecomAccessTokenProvider tokenProvider;
  private final WecomRelayClient relayClient;
  private final WecomHttpTransport httpTransport;
  private final LongSupplier ticker;

  @Autowired
  public WecomSmartSheetApiClient(
      ObjectMapper objectMapper,
      WecomSmartSheetConfig config,
      WecomAccessTokenProvider tokenProvider,
      WecomRelayClient relayClient) {
    this(objectMapper, config, tokenProvider, relayClient, new WecomUrlConnectionTransport(), System::nanoTime);
  }

  WecomSmartSheetApiClient(
      ObjectMapper objectMapper,
      WecomSmartSheetConfig config,
      WecomAccessTokenProvider tokenProvider) {
    this(objectMapper, config, tokenProvider, new WecomRelayClient(objectMapper, config));
  }

  WecomSmartSheetApiClient(
      ObjectMapper objectMapper,
      WecomSmartSheetConfig config,
      WecomAccessTokenProvider tokenProvider,
      HttpClient httpClient) {
    this(objectMapper, config, tokenProvider, new WecomRelayClient(objectMapper, config),
        WecomHttpTransport.from(httpClient), System::nanoTime);
  }

  WecomSmartSheetApiClient(
      ObjectMapper objectMapper,
      WecomSmartSheetConfig config,
      WecomAccessTokenProvider tokenProvider,
      HttpClient httpClient,
      LongSupplier ticker) {
    this(objectMapper, config, tokenProvider, new WecomRelayClient(objectMapper, config),
        WecomHttpTransport.from(httpClient), ticker);
  }

  private WecomSmartSheetApiClient(
      ObjectMapper objectMapper,
      WecomSmartSheetConfig config,
      WecomAccessTokenProvider tokenProvider,
      WecomRelayClient relayClient,
      WecomHttpTransport httpTransport,
      LongSupplier ticker) {
    this.objectMapper = objectMapper;
    this.config = config;
    this.tokenProvider = tokenProvider;
    this.relayClient = relayClient;
    this.httpTransport = httpTransport;
    this.ticker = ticker;
  }

  public JsonNode post(String operation, Object body, Duration timeout) {
    return post(operation, body, timeout, true);
  }

  public JsonNode postWithApplicationCredentials(String operation, Object body, Duration timeout) {
    return post(operation, body, timeout, false);
  }

  private JsonNode post(String operation, Object body, Duration timeout, boolean requireTargetConfiguration) {
    if (operation == null) {
      throw failure(SAFE_OPERATION, "unsupported operation");
    }
    String path = PATHS.get(operation);
    if (path == null) {
      throw failure(SAFE_OPERATION, "unsupported operation");
    }
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw failure(operation, "request timeout must be positive");
    }
    if (config.transportMode() == WecomTransportMode.RELAY) {
      return relayClient.post(operation, body, timeout);
    }
    WecomRequestDeadline deadline = WecomRequestDeadline.start(timeout, operation, ticker);
    try {
      if (requireTargetConfiguration) {
        config.requireConfigured();
      } else {
        config.requireApplicationCredentials();
      }
    } catch (IllegalStateException ex) {
      throw failure(operation, ex.getMessage());
    } catch (RuntimeException ex) {
      throw failure(operation, "configuration is incomplete");
    }

    String json = serialize(operation, body);
    for (int attempt = 0; attempt < 2; attempt++) {
      String token = token(operation, deadline.remaining());
      Response response = send(operation, path, token, json, deadline.remaining());
      if (response.errcode() == 0) {
        deadline.remaining();
        return response.root();
      }
      if (isTokenError(response.errcode()) && attempt == 0) {
        invalidate(operation, token);
        continue;
      }
      throw new WecomSmartSheetException(operation, response.errcode(), "remote API returned an error");
    }
    throw failure(operation, "request failed");
  }

  private String serialize(String operation, Object body) {
    try {
      return objectMapper.writeValueAsString(body);
    } catch (JsonProcessingException | RuntimeException ex) {
      throw failure(operation, "request body could not be serialized");
    }
  }

  private String token(String operation, Duration timeout) {
    try {
      return tokenProvider.get(timeout);
    } catch (WecomSmartSheetException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw failure(operation, "access token could not be obtained");
    }
  }

  private void invalidate(String operation, String token) {
    try {
      tokenProvider.invalidate(token);
    } catch (WecomSmartSheetException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw failure(operation, "access token could not be invalidated");
    }
  }

  private Response send(String operation, String path, String token, String json, Duration timeout) {
    WecomHttpResponse response;
    try {
      URI uri = URI.create(config.apiBaseUrl() + path + "?access_token=" + encode(token));
      response = httpTransport.send(
          uri,
          "POST",
          Map.of("Content-Type", "application/json"),
          json.getBytes(StandardCharsets.UTF_8),
          timeout);
    } catch (IOException ex) {
      throw failure(operation, "network request failed");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw failure(operation, "request interrupted");
    } catch (IllegalArgumentException ex) {
      throw failure(operation, "request configuration was invalid");
    }

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw failure(operation, "HTTP status " + response.statusCode());
    }
    JsonNode root;
    try {
      ObjectReader reader = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
      root = reader.readTree(response.body());
    } catch (IOException | RuntimeException ex) {
      throw failure(operation, "response was not valid JSON");
    }
    if (root == null || root.isMissingNode()) {
      throw failure(operation, "response was not valid JSON");
    }
    if (!root.isObject()) {
      throw failure(operation, "response was not a JSON object");
    }
    JsonNode errcode = root.get("errcode");
    if (errcode == null || !errcode.isIntegralNumber() || !errcode.canConvertToInt()) {
      throw failure(operation, "response missing valid errcode");
    }
    return new Response(root, errcode.intValue());
  }

  private static boolean isTokenError(int errcode) {
    return errcode == 40014 || errcode == 42001;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static WecomSmartSheetException failure(String operation, String reason) {
    return new WecomSmartSheetException(operation, reason, null);
  }

  private record Response(JsonNode root, int errcode) {}
}
