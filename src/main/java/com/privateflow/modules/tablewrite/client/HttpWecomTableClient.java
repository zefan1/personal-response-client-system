package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.sync.SheetClient;
import com.privateflow.modules.customer.sync.SheetRow;
import com.privateflow.modules.tablewrite.config.TableConfig;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mock-externals", havingValue = "false", matchIfMissing = true)
public class HttpWecomTableClient implements WecomTableClient, SheetClient {

  private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final TableConfigProvider configProvider;

  public HttpWecomTableClient(ObjectMapper objectMapper, TableConfigProvider configProvider) {
    this.objectMapper = objectMapper;
    this.configProvider = configProvider;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .version(HttpClient.Version.HTTP_1_1)
        .build();
  }

  @Override
  public List<SheetRow> fetchIncrementalRows(String sourceTable, LocalDateTime modifiedAfter, int limit) {
    TableConfig config = requireConfig();
    String path = "/tables/" + encode(sourceTable) + "/rows"
        + "?modifiedAfter=" + encode(modifiedAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        + "&limit=" + limit;
    JsonNode root = send(config, "GET", path, null, Duration.ofMillis(config.writeTimeoutMs()));
    JsonNode rows = root.path("rows");
    if (!rows.isArray()) {
      rows = root.path("data").path("rows");
    }
    if (!rows.isArray()) {
      throw new IllegalStateException("WeCom sheet response missing rows array");
    }
    return objectMapper.convertValue(rows, new TypeReference<List<JsonNode>>() {}).stream()
        .map(this::toSheetRow)
        .toList();
  }

  @Override
  public String createRow(String sourceTable, Map<String, Object> fields, Duration timeout) {
    TableConfig config = requireConfig();
    JsonNode root = send(config, "POST", "/tables/" + encode(sourceTable) + "/rows", Map.of("fields", fields), timeout);
    String rowId = text(root, "rowId");
    if (rowId == null) {
      rowId = text(root.path("data"), "rowId");
    }
    if (rowId == null) {
      rowId = text(root, "id");
    }
    if (rowId == null || rowId.isBlank()) {
      throw new IllegalStateException("WeCom create row response missing rowId");
    }
    return rowId;
  }

  @Override
  public void updateRow(String sourceTable, String sourceRowId, Map<String, Object> fields, Duration timeout) {
    TableConfig config = requireConfig();
    send(config, "PUT", "/tables/" + encode(sourceTable) + "/rows/" + encode(sourceRowId), Map.of("fields", fields), timeout);
  }

  private TableConfig requireConfig() {
    TableConfig config = configProvider.get();
    if (config.apiBaseUrl() == null || config.apiBaseUrl().isBlank()) {
      throw new IllegalStateException("table.api_base_url is required when MOCK_EXTERNALS=false");
    }
    if (config.apiKey() == null || config.apiKey().isBlank()) {
      throw new IllegalStateException("table.api_key is required when MOCK_EXTERNALS=false");
    }
    return config;
  }

  private JsonNode send(TableConfig config, String method, String path, Object body, Duration timeout) {
    try {
      HttpRequest.Builder builder = HttpRequest.newBuilder()
          .uri(URI.create(config.apiBaseUrl().replaceAll("/+$", "") + path))
          .timeout(timeout)
          .header("Authorization", "Bearer " + config.apiKey())
          .header("Content-Type", "application/json");
      if ("GET".equals(method)) {
        builder.GET();
      } else if ("POST".equals(method)) {
        builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
      } else if ("PUT".equals(method)) {
        builder.PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
      } else {
        throw new IllegalArgumentException("Unsupported method " + method);
      }
      HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("WeCom table gateway returned HTTP " + response.statusCode());
      }
      if (response.body() == null || response.body().isBlank()) {
        return objectMapper.createObjectNode();
      }
      return objectMapper.readTree(response.body());
    } catch (IOException ex) {
      throw new IllegalStateException("WeCom table gateway request failed", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("WeCom table gateway request interrupted", ex);
    }
  }

  private SheetRow toSheetRow(JsonNode node) {
    String rowId = text(node, "rowId");
    if (rowId == null) {
      rowId = text(node, "id");
    }
    if (rowId == null || rowId.isBlank()) {
      throw new IllegalStateException("WeCom row missing rowId");
    }
    JsonNode fields = node.path("fields");
    if (!fields.isObject()) {
      fields = node.path("values");
    }
    if (!fields.isObject()) {
      fields = objectMapper.createObjectNode();
    }
    Map<String, String> values = new LinkedHashMap<>(objectMapper.convertValue(fields, STRING_MAP));
    return new SheetRow(rowId, values);
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.path(field);
    return value == null || value.isMissingNode() || value.isNull() ? null : value.asText();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
