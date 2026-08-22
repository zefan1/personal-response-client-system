package com.privateflow.modules.customer.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.config.ConfigAdminService;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetApiClient;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetFieldMetadata;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetFieldCatalog;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SmartSheetConnectionService {

  private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(10);

  private final WecomSmartSheetConfig config;
  private final AuxiliarySmartSheetTargets auxiliaryTargets;
  private final WecomSmartSheetApiClient apiClient;
  private final ConfigAdminService configAdminService;
  private final DatasourceAdminRepository datasourceRepository;
  private final WecomSmartSheetFieldCatalog fieldCatalog;

  @Autowired
  public SmartSheetConnectionService(
      WecomSmartSheetConfig config,
      AuxiliarySmartSheetTargets auxiliaryTargets,
      WecomSmartSheetApiClient apiClient,
      ConfigAdminService configAdminService,
      DatasourceAdminRepository datasourceRepository,
      WecomSmartSheetFieldCatalog fieldCatalog) {
    this.config = config;
    this.auxiliaryTargets = auxiliaryTargets;
    this.apiClient = apiClient;
    this.configAdminService = configAdminService;
    this.datasourceRepository = datasourceRepository;
    this.fieldCatalog = fieldCatalog;
  }

  public SmartSheetConnectionService(
      WecomSmartSheetConfig config,
      AuxiliarySmartSheetTargets auxiliaryTargets,
      WecomSmartSheetApiClient apiClient,
      ConfigAdminService configAdminService) {
    this(config, auxiliaryTargets, apiClient, configAdminService, null, null);
  }

  public SmartSheetConnectionService(
      WecomSmartSheetConfig config,
      WecomSmartSheetApiClient apiClient,
      ConfigAdminService configAdminService) {
    this(config, new AuxiliarySmartSheetTargets(), apiClient, configAdminService, null, null);
  }

  public SmartSheetConnectionResult verifyAndSave(SmartSheetConnectionRequest request) {
    String documentUrl = validDocumentUrl(request == null ? null : request.documentUrl());
    String role = validRole(request == null ? null : request.role());
    Target previous = currentTarget(role);
    Target requested = requestedTarget(role, documentUrl, request, previous);

    if (requested.documentId().isBlank()) {
      throw badRequest("系统无法从分享网址中读取文档 ID，请展开“更换表格时使用”，填写文档 ID 后重试");
    }
    if (urlChangedWithoutNewDocumentId(documentUrl, requested, previous)) {
      throw badRequest("你粘贴了另一张表的网址，请展开“更换表格时使用”，同时填写新表格的文档 ID");
    }

    try {
      JsonNode sheets = apiClient.postWithApplicationCredentials(
          "get_sheet", Map.of("docid", requested.documentId()), VERIFY_TIMEOUT).get("sheet_list");
      JsonNode sheet = requested.sheetId().isBlank()
          ? firstItem(sheets)
          : findById(sheets, "sheet_id", requested.sheetId());
      if (sheet == null && requested.sheetId().equals(queryParameter(documentUrl, "tab"))) {
        sheet = firstItem(sheets);
      }
      if (sheet == null) {
        throw badRequest("文档 ID 可以访问，但没有找到这张子表，请检查子表 ID");
      }
      String sheetId = firstText(sheet, "sheet_id");

      JsonNode views = apiClient.postWithApplicationCredentials("get_views", Map.of(
          "docid", requested.documentId(),
          "sheet_id", sheetId,
          "offset", 0,
          "limit", 1000), VERIFY_TIMEOUT).get("views");
      JsonNode view = requested.viewId().isBlank()
          ? firstItem(views)
          : findById(views, "view_id", requested.viewId());
      if (view == null && request != null && (request.viewId() == null || request.viewId().isBlank())) {
        view = firstItem(views);
      }
      if (view == null) {
        throw badRequest("子表可以访问，但没有找到这个视图；可清空视图 ID，让系统自动选择第一个视图");
      }
      String viewId = firstText(view, "view_id");

      JsonNode fields = apiClient.postWithApplicationCredentials("get_fields", Map.of(
          "docid", requested.documentId(),
          "sheet_id", sheetId,
          "view_id", viewId,
          "offset", 0,
          "limit", 1000), VERIFY_TIMEOUT).get("fields");
      String uniqueFieldTitle = requested.uniqueFieldTitle();
      JsonNode uniqueField = uniqueFieldTitle.isBlank()
          ? null
          : findFieldByTitle(fields, uniqueFieldTitle);
      if (uniqueField == null && uniqueFieldTitle.isBlank()) {
        uniqueField = firstField(fields);
      }
      if (uniqueField != null) {
        uniqueFieldTitle = WecomSmartSheetFieldMetadata.title(uniqueField);
      }
      if (uniqueField == null) {
        throw badRequest("表格可以访问，但没有返回可用列；请先识别列名，再将实际手机号列映射为系统内容“手机号”");
      }

      Target verified = new Target(
          requested.documentId(), sheetId, viewId, uniqueFieldTitle, documentUrl, requested.prefix());
      configAdminService.updateAll(configValues(verified));
      if (fieldCatalog != null) {
        fieldCatalog.invalidate();
      }
      if (datasourceRepository != null) {
        if ("PRIMARY".equals(role)) {
          datasourceRepository.updateApiOwnedSmartSheetTarget(verified.documentId(), verified.sheetId());
        }
        datasourceRepository.ensureManagedSmartSheetDatasource(role, verified.documentId(), verified.sheetId());
      }
      return new SmartSheetConnectionResult(
          true,
          role,
          firstText(sheet, "sheet_name", "title", "name"),
          verified.documentId(),
          verified.sheetId(),
          verified.viewId(),
          verified.uniqueFieldTitle(),
          documentUrl);
    } catch (ApiException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw badRequest("暂时无法读取这张表格，请检查企业微信连接、文档 ID 和应用权限后重试");
    }
  }

  private Target requestedTarget(
      String role,
      String documentUrl,
      SmartSheetConnectionRequest request,
      Target previous) {
    String tab = queryParameter(documentUrl, "tab");
    String urlDocumentId = documentIdFromUrl(documentUrl);
    String urlSheetId = firstNonBlank(
        queryParameter(documentUrl, "sheet_id"),
        queryParameter(documentUrl, "sheetid"),
        tab);
    String urlViewId = firstNonBlank(
        queryParameter(documentUrl, "view_id"),
        queryParameter(documentUrl, "viewid"),
        queryParameter(documentUrl, "viewId"));
    String explicitDocumentId = request == null ? "" : request.documentId();
    String explicitSheetId = request == null ? "" : request.sheetId();
    String explicitViewId = request == null ? "" : request.viewId();
    String resolvedDocumentId = firstNonBlank(explicitDocumentId, urlDocumentId, previous.documentId());
    String fallbackSheetId = resolvedDocumentId.equals(previous.documentId()) ? previous.sheetId() : "";
    String fallbackViewId = resolvedDocumentId.equals(previous.documentId()) ? previous.viewId() : "";
    return new Target(
        resolvedDocumentId,
        firstNonBlank(explicitSheetId, urlSheetId, fallbackSheetId),
        firstNonBlank(explicitViewId, urlViewId, fallbackViewId),
        request == null || request.uniqueFieldTitle() == null ? "" : request.uniqueFieldTitle().trim(),
        documentUrl,
        prefix(role));
  }

  private String documentIdFromUrl(String documentUrl) {
    try {
      URI uri = URI.create(documentUrl);
      String queryDocumentId = firstNonBlank(queryParameter(documentUrl, "docid"), queryParameter(documentUrl, "doc_id"));
      if (!queryDocumentId.isBlank()) {
        return queryDocumentId;
      }
      String[] parts = uri.getPath().split("/");
      for (int i = 0; i + 1 < parts.length; i++) {
        if (("sheet".equalsIgnoreCase(parts[i]) || "smartsheet".equalsIgnoreCase(parts[i]))
          && !parts[i + 1].isBlank()) {
          String candidate = URLDecoder.decode(parts[i + 1], StandardCharsets.UTF_8).trim();
          // s3_... is the browser share token, not the API docid. Keep the
          // existing verified API target for the three fixed tables.
          return candidate.startsWith("s3_") ? "" : candidate;
        }
      }
    } catch (RuntimeException ignored) {
      // URL validation happens before this method.
    }
    return "";
  }

  private Target currentTarget(String role) {
    if ("PRIMARY".equals(role)) {
      return new Target(
          config.documentId(), config.sheetId(), config.viewId(), config.uniqueFieldTitle(), "", "table.primary");
    }
    AuxiliarySmartSheetTarget target = auxiliaryTargets.forRole(role).orElseGet(() ->
        new AuxiliarySmartSheetTarget(role, "", "", "", "", ""));
    return new Target(
        target.documentId(), target.sheetId(), target.viewId(), target.uniqueFieldTitle(),
        target.documentUrl(), prefix(role));
  }

  private Map<String, String> configValues(Target target) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(target.prefix() + ".document_id", target.documentId());
    values.put(target.prefix() + ".sheet_id", target.sheetId());
    values.put(target.prefix() + ".view_id", target.viewId());
    values.put(target.prefix() + ".unique_field_title", target.uniqueFieldTitle());
    if ("table.primary".equals(target.prefix())) {
      values.put("table.primary.source_table", target.sheetId());
      values.put("table.document_url", target.documentUrl());
    } else {
      values.put(target.prefix() + "_document_url", target.documentUrl());
    }
    return values;
  }

  private boolean urlChangedWithoutNewDocumentId(String documentUrl, Target requested, Target previous) {
    if (previous.documentId().isBlank() || previous.documentUrl().isBlank()
        || !requested.documentId().equals(previous.documentId())) {
      return false;
    }
    if (URLDecoder.decode(documentUrl, StandardCharsets.UTF_8).contains(requested.documentId())) {
      return false;
    }
    return !sameDocumentPath(documentUrl, previous.documentUrl());
  }

  private boolean sameDocumentPath(String actualUrl, String expectedUrl) {
    try {
      URI actual = URI.create(actualUrl);
      URI expected = URI.create(expectedUrl);
      return actual.getHost() != null
          && actual.getHost().equalsIgnoreCase(expected.getHost())
          && actual.getPath().equals(expected.getPath());
    } catch (RuntimeException ex) {
      return false;
    }
  }

  private String queryParameter(String documentUrl, String name) {
    try {
      String query = URI.create(documentUrl).getRawQuery();
      if (query == null) {
        return "";
      }
      for (String pair : query.split("&")) {
        String[] parts = pair.split("=", 2);
        if (name.equals(parts[0]) && parts.length == 2) {
          return URLDecoder.decode(parts[1], StandardCharsets.UTF_8).trim();
        }
      }
    } catch (RuntimeException ignored) {
      // The URL itself was already validated; an invalid query value is simply ignored.
    }
    return "";
  }

  private String validRole(String raw) {
    String role = raw == null || raw.isBlank() ? "PRIMARY" : raw.trim().toUpperCase();
    if (!"PRIMARY".equals(role) && !"ASSIGNMENT".equals(role) && !"ARRIVAL".equals(role)) {
      throw badRequest("请选择客户主表、分配表或到店表");
    }
    return role;
  }

  private String prefix(String role) {
    return switch (role) {
      case "ASSIGNMENT" -> "table.assignment";
      case "ARRIVAL" -> "table.arrival";
      default -> "table.primary";
    };
  }

  private boolean nullOrWithoutUniqueField(SmartSheetConnectionRequest request) {
    return request == null || request.uniqueFieldTitle() == null || request.uniqueFieldTitle().isBlank();
  }

  private JsonNode firstField(JsonNode fields) {
    if (fields == null || !fields.isArray()) {
      return null;
    }
    for (JsonNode field : fields) {
      String title = WecomSmartSheetFieldMetadata.title(field);
      if (!title.isBlank()) {
        return field;
      }
    }
    return null;
  }

  private String validDocumentUrl(String raw) {
    String value = raw == null ? "" : raw.trim();
    try {
      URI uri = URI.create(value);
      String scheme = uri.getScheme();
      if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
        throw new IllegalArgumentException("unsupported URL");
      }
      return value;
    } catch (RuntimeException ex) {
      throw badRequest("请打开目标表格并复制浏览器地址栏里的完整链接");
    }
  }

  private JsonNode firstItem(JsonNode items) {
    return items != null && items.isArray() && !items.isEmpty() ? items.get(0) : null;
  }

  private JsonNode findById(JsonNode items, String field, String expected) {
    if (items == null || !items.isArray()) {
      return null;
    }
    for (JsonNode item : items) {
      if (expected.equals(item.path(field).asText(""))) {
        return item;
      }
    }
    return null;
  }

  private JsonNode findByText(JsonNode items, String expected, String... fields) {
    if (items == null || !items.isArray()) {
      return null;
    }
    for (JsonNode item : items) {
      for (String field : fields) {
        if (expected.equals(item.path(field).asText("").trim())) {
          return item;
        }
      }
    }
    return null;
  }

  private String firstText(JsonNode node, String... fields) {
    if (node != null) {
      for (String field : fields) {
        String value = node.path(field).asText("").trim();
        if (!value.isEmpty()) {
          return value;
        }
      }
    }
    return "已连接的 API 表格";
  }

  private JsonNode findFieldByTitle(JsonNode fields, String expected) {
    if (fields == null || !fields.isArray()) {
      return null;
    }
    for (JsonNode field : fields) {
      if (expected.equals(WecomSmartSheetFieldMetadata.title(field))) {
        return field;
      }
    }
    return null;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return "";
  }

  private String providedOrFallback(String provided, String fallback) {
    return provided == null ? firstNonBlank(fallback) : provided.trim();
  }

  private ApiException badRequest(String message) {
    return new ApiException(ApiErrorCodes.BAD_REQUEST, message);
  }

  private record Target(
      String documentId,
      String sheetId,
      String viewId,
      String uniqueFieldTitle,
      String documentUrl,
      String prefix) {
  }
}
