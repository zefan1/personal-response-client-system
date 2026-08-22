package com.privateflow.modules.tablewrite.config;

import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final WecomApiEndpointProvider endpointProvider;
  private final String relayBaseUrl;
  private final String relayKeyId;
  private final String relaySecret;
  private final WecomTransportMode configuredTransportMode;
  private final SystemConfigRepository runtimeConfigRepository;

  @Autowired
  public WecomSmartSheetConfig(
      @Value("${WECOM_API_BASE_URL:https://qyapi.weixin.qq.com}") String apiBaseUrl,
      @Value("${WECOM_CORP_ID:}") String corpId,
      @Value("${WECOM_APP_SECRET:}") String appSecret,
      @Value("${WECOM_SMARTSHEET_DOC_ID:}") String documentId,
      @Value("${WECOM_SMARTSHEET_SHEET_ID:}") String sheetId,
      @Value("${WECOM_SMARTSHEET_VIEW_ID:}") String viewId,
      @Value("${WECOM_SMARTSHEET_SOURCE_TABLE:}") String sourceTable,
      @Value("${WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE:}") String uniqueFieldTitle,
      @Value("${WECOM_TRANSPORT_MODE:DIRECT}") String transportMode,
      @Value("${WECOM_RELAY_BASE_URL:}") String relayBaseUrl,
      @Value("${WECOM_RELAY_KEY_ID:}") String relayKeyId,
      @Value("${WECOM_RELAY_SECRET:}") String relaySecret,
      WecomApiEndpointProvider endpointProvider,
      SystemConfigRepository runtimeConfigRepository) {
    this(apiBaseUrl, corpId, appSecret, documentId, sheetId, viewId, sourceTable, uniqueFieldTitle,
        ZoneId.of("Asia/Shanghai"), endpointProvider, WecomTransportMode.from(transportMode), relayBaseUrl,
        relayKeyId, relaySecret, runtimeConfigRepository);
  }

  public WecomSmartSheetConfig(
      String apiBaseUrl,
      String corpId,
      String appSecret,
      String documentId,
      String sheetId,
      String viewId,
      String sourceTable,
      String uniqueFieldTitle) {
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
    this(apiBaseUrl, corpId, appSecret, documentId, sheetId, viewId, sourceTable, uniqueFieldTitle, zoneId,
        null, WecomTransportMode.DIRECT, "", "", "", null);
  }

  WecomSmartSheetConfig(
      String apiBaseUrl,
      String corpId,
      String appSecret,
      String documentId,
      String sheetId,
      String viewId,
      String sourceTable,
      String uniqueFieldTitle,
      ZoneId zoneId,
      WecomApiEndpointProvider endpointProvider) {
    this(apiBaseUrl, corpId, appSecret, documentId, sheetId, viewId, sourceTable, uniqueFieldTitle, zoneId,
        endpointProvider, WecomTransportMode.DIRECT, "", "", "", null);
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
      ZoneId zoneId,
      WecomTransportMode transportMode,
      WecomRelayConfig relayConfig) {
    this(apiBaseUrl, corpId, appSecret, documentId, sheetId, viewId, sourceTable, uniqueFieldTitle, zoneId,
        null, transportMode, relayConfig.baseUrl(), relayConfig.keyId(), relayConfig.secret(), null);
  }

  private WecomSmartSheetConfig(
      String apiBaseUrl,
      String corpId,
      String appSecret,
      String documentId,
      String sheetId,
      String viewId,
      String sourceTable,
      String uniqueFieldTitle,
      ZoneId zoneId,
      WecomApiEndpointProvider endpointProvider,
      WecomTransportMode transportMode,
      String relayBaseUrl,
      String relayKeyId,
      String relaySecret,
      SystemConfigRepository runtimeConfigRepository) {
    this.apiBaseUrl = normalizedBaseUrl(apiBaseUrl);
    this.corpId = trimmed(corpId);
    this.appSecret = trimmed(appSecret);
    this.documentId = trimmed(documentId);
    this.sheetId = trimmed(sheetId);
    this.viewId = trimmed(viewId);
    this.sourceTable = trimmed(sourceTable);
    this.uniqueFieldTitle = trimmed(uniqueFieldTitle);
    this.zoneId = zoneId;
    this.endpointProvider = endpointProvider;
    this.configuredTransportMode = transportMode == null ? WecomTransportMode.DIRECT : transportMode;
    this.relayBaseUrl = normalizedBaseUrl(relayBaseUrl);
    this.relayKeyId = trimmed(relayKeyId);
    this.relaySecret = trimmed(relaySecret);
    this.runtimeConfigRepository = runtimeConfigRepository;
  }

  public void requireConfigured() {
    List<String> missing = new ArrayList<>();
    addMissingApplicationCredentials(missing);
    require(missing, documentId(), "WECOM_SMARTSHEET_DOC_ID");
    require(missing, sheetId(), "WECOM_SMARTSHEET_SHEET_ID");
    require(missing, viewId(), "WECOM_SMARTSHEET_VIEW_ID");
    require(missing, sourceTable(), "WECOM_SMARTSHEET_SOURCE_TABLE");
    require(missing, uniqueFieldTitle(), "WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE");
    if (!missing.isEmpty()) {
      throw new IllegalStateException("Missing required environment variables: " + String.join(", ", missing));
    }
  }

  public void requireApplicationCredentials() {
    List<String> missing = new ArrayList<>();
    addMissingApplicationCredentials(missing);
    if (!missing.isEmpty()) {
      throw new IllegalStateException("Missing required environment variables: " + String.join(", ", missing));
    }
  }

  public void requireTarget(String requestedDocumentId, String requestedSourceTable) {
    requireConfigured();
    if (!documentId().equals(trimmed(requestedDocumentId))) {
      throw new IllegalArgumentException("Requested document does not match configured document");
    }
    if (!sourceTable().equals(trimmed(requestedSourceTable))) {
      throw new IllegalArgumentException("Requested source table does not match configured source table");
    }
  }

  public String apiBaseUrl() {
    return endpointProvider == null ? apiBaseUrl : endpointProvider.currentBaseUrl(apiBaseUrl);
  }

  public String corpId() {
    return corpId;
  }

  public String appSecret() {
    return appSecret;
  }

  public String documentId() {
    return runtimeValue("table.primary.document_id", documentId);
  }

  public String sheetId() {
    return runtimeValue("table.primary.sheet_id", sheetId);
  }

  public String viewId() {
    return runtimeValue("table.primary.view_id", viewId);
  }

  public String sourceTable() {
    return runtimeValue("table.primary.source_table", sourceTable);
  }

  public String uniqueFieldTitle() {
    return runtimeValue("table.primary.unique_field_title", uniqueFieldTitle);
  }

  public ZoneId zoneId() {
    return zoneId;
  }

  public WecomTransportMode transportMode() {
    return endpointProvider == null ? configuredTransportMode : endpointProvider.currentMode();
  }

  public WecomRelayConfig relayConfig() {
    String baseUrl = endpointProvider == null
        ? relayBaseUrl
        : endpointProvider.currentRelayBaseUrl(relayBaseUrl);
    return new WecomRelayConfig(baseUrl, relayKeyId, relaySecret);
  }

  private static void require(List<String> missing, String value, String environmentVariable) {
    if (value.isBlank()) {
      missing.add(environmentVariable);
    }
  }

  private void addMissingApplicationCredentials(List<String> missing) {
    require(missing, corpId, "WECOM_CORP_ID");
    require(missing, appSecret, "WECOM_APP_SECRET");
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

  private String runtimeValue(String key, String fallback) {
    if (runtimeConfigRepository == null) {
      return fallback;
    }
    return runtimeConfigRepository.findValue(key)
        .map(WecomSmartSheetConfig::trimmed)
        .filter(value -> !value.isBlank())
        .orElse(fallback);
  }
}
