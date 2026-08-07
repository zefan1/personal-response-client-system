package com.privateflow.modules.customer.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.config.ConfigAdminService;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetApiClient;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SmartSheetConnectionService {

  private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(10);

  private final WecomSmartSheetConfig config;
  private final WecomSmartSheetApiClient apiClient;
  private final ConfigAdminService configAdminService;

  public SmartSheetConnectionService(
      WecomSmartSheetConfig config,
      WecomSmartSheetApiClient apiClient,
      ConfigAdminService configAdminService) {
    this.config = config;
    this.apiClient = apiClient;
    this.configAdminService = configAdminService;
  }

  public SmartSheetConnectionResult verifyAndSave(SmartSheetConnectionRequest request) {
    String documentUrl = validDocumentUrl(request == null ? null : request.documentUrl());
    try {
      config.requireConfigured();
    } catch (RuntimeException ex) {
      throw badRequest("服务器尚未完成企业微信 API 表格配置，请先完成服务器部署配置");
    }

    String decodedUrl = URLDecoder.decode(documentUrl, StandardCharsets.UTF_8);
    if (!decodedUrl.contains(config.documentId())) {
      throw badRequest("该表格不是本系统通过企业微信 API 创建并纳入的数据表");
    }

    try {
      JsonNode sheet = findById(
          apiClient.post("get_sheet", Map.of("docid", config.documentId()), VERIFY_TIMEOUT).get("sheet_list"),
          "sheet_id",
          config.sheetId());
      if (sheet == null) {
        throw badRequest("这个文档里没有找到本系统创建的子表");
      }

      JsonNode view = findById(
          apiClient.post("get_views", Map.of(
              "docid", config.documentId(),
              "sheet_id", config.sheetId(),
              "offset", 0,
              "limit", 1000), VERIFY_TIMEOUT).get("views"),
          "view_id",
          config.viewId());
      if (view == null) {
        throw badRequest("这个子表里没有找到本系统创建的表格视图");
      }

      configAdminService.update("table.document_url", Map.of("value", documentUrl));
      return new SmartSheetConnectionResult(
          true,
          firstText(sheet, "sheet_name", "title", "name"),
          config.documentId(),
          config.sheetId(),
          config.viewId(),
          documentUrl);
    } catch (ApiException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw badRequest("暂时无法读取这张表格，请检查企业微信连接后重试");
    }
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

  private String firstText(JsonNode node, String... fields) {
    for (String field : fields) {
      String value = node.path(field).asText("").trim();
      if (!value.isEmpty()) {
        return value;
      }
    }
    return "已连接的 API 表格";
  }

  private ApiException badRequest(String message) {
    return new ApiException(ApiErrorCodes.BAD_REQUEST, message);
  }
}
