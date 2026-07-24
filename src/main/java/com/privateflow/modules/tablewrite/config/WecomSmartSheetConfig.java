package com.privateflow.modules.tablewrite.config;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class WecomSmartSheetConfig {

  private final String apiBaseUrl;
  private final String corpId;
  private final String appSecret;
  private final String documentId;
  private final String sheetId;
  private final String viewId;
  private final String sourceTable;
  private final String uniqueFieldTitle;
  private final ZoneId zoneId;

  public WecomSmartSheetConfig(
      @Value("${WECOM_API_BASE_URL:https://qyapi.weixin.qq.com}") String apiBaseUrl,
      @Value("${WECOM_CORP_ID:}") String corpId,
      @Value("${WECOM_APP_SECRET:}") String appSecret,
      @Value("${WECOM_SMARTSHEET_DOC_ID:}") String documentId,
      @Value("${WECOM_SMARTSHEET_SHEET_ID:}") String sheetId,
      @Value("${WECOM_SMARTSHEET_VIEW_ID:}") String viewId,
      @Value("${WECOM_SMARTSHEET_SOURCE_TABLE:}") String sourceTable,
      @Value("${WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE:}") String uniqueFieldTitle) {
    this(apiBaseUrl, corpId, appSecret, documentId, sheetId, viewId, sourceTable, uniqueFieldTitle,
        ZoneId.of("Asia/Shanghai"));
  }

  public WecomSmartSheetConfig(
      String apiBaseUrl,
      String corpId,
      String appSecret,
      String documentId,
      String sheetId,
      String viewId,
      String sourceTable,
      String uniqueFieldTitle,
      ZoneId zoneId) {
    this.apiBaseUrl = normalizedBaseUrl(apiBaseUrl);
    this.corpId = trimmed(corpId);
    this.appSecret = trimmed(appSecret);
    this.documentId = trimmed(documentId);
    this.sheetId = trimmed(sheetId);
    this.viewId = trimmed(viewId);
    this.sourceTable = trimmed(sourceTable);
    this.uniqueFieldTitle = trimmed(uniqueFieldTitle);
    this.zoneId = zoneId;
  }

  public void requireConfigured() {
    List<String> missing = new ArrayList<>();
    require(missing, corpId, "WECOM_CORP_ID");
    require(missing, appSecret, "WECOM_APP_SECRET");
    require(missing, documentId, "WECOM_SMARTSHEET_DOC_ID");
    require(missing, sheetId, "WECOM_SMARTSHEET_SHEET_ID");
    require(missing, viewId, "WECOM_SMARTSHEET_VIEW_ID");
    require(missing, sourceTable, "WECOM_SMARTSHEET_SOURCE_TABLE");
    require(missing, uniqueFieldTitle, "WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE");
    if (!missing.isEmpty()) {
      throw new IllegalStateException("Missing required environment variables: " + String.join(", ", missing));
    }
  }

  public void requireTarget(String requestedDocumentId, String requestedSourceTable) {
    requireConfigured();
    if (!documentId.equals(trimmed(requestedDocumentId))) {
      throw new IllegalStateException("Requested document does not match configured document");
    }
    if (!sourceTable.equals(trimmed(requestedSourceTable))) {
      throw new IllegalStateException("Requested source table does not match configured source table");
    }
  }

  public String apiBaseUrl() {
    return apiBaseUrl;
  }

  public String corpId() {
    return corpId;
  }

  public String appSecret() {
    return appSecret;
  }

  public String documentId() {
    return documentId;
  }

  public String sheetId() {
    return sheetId;
  }

  public String viewId() {
    return viewId;
  }

  public String sourceTable() {
    return sourceTable;
  }

  public String uniqueFieldTitle() {
    return uniqueFieldTitle;
  }

  public ZoneId zoneId() {
    return zoneId;
  }

  private static void require(List<String> missing, String value, String environmentVariable) {
    if (value.isBlank()) {
      missing.add(environmentVariable);
    }
  }

  private static String normalizedBaseUrl(String value) {
    String normalized = trimmed(value);
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private static String trimmed(String value) {
    return value == null ? "" : value.trim();
  }
}
