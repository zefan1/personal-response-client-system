package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.tablewrite.config.WecomRelayConfig;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import com.privateflow.modules.tablewrite.config.WecomTransportMode;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Entrypoint for the controlled real-provider acceptance run. */
public final class WecomSmartSheetLiveAcceptanceMain {

  private static final String DEFAULT_API_BASE_URL = "https://qyapi.weixin.qq.com";
  private static final Duration ACCEPTANCE_TIMEOUT = Duration.ofSeconds(15);

  private WecomSmartSheetLiveAcceptanceMain() {}

  public static void main(String[] args) {
    try {
      WecomSmartSheetLiveAcceptanceService.Report report =
          createService(System.getenv(), () -> UUID.randomUUID().toString()).run();
      System.out.printf(
          "WeCom Smart Sheet acceptance passed: query=%s, create=true, update=%s, duplicate=%s, reread=%s, formulaProtection=%s, recordId=%s%n",
          report.querySucceeded(),
          report.updateSucceeded(),
          report.duplicatePrevented(),
          report.rereadSucceeded(),
          report.formulaProtectionConfirmed(),
          report.createdRecordId());
    } catch (RuntimeException exception) {
      String message = exception.getMessage();
      System.err.println("WeCom Smart Sheet acceptance failed: "
          + (message == null || message.isBlank() ? "unexpected runtime failure" : message));
      System.exit(1);
    }
  }

  static WecomSmartSheetLiveAcceptanceService createService(
      Map<String, String> environment, Supplier<String> tokenSuffixSupplier) {
    WecomSmartSheetConfig config = new WecomSmartSheetConfig(
        environment.getOrDefault("WECOM_API_BASE_URL", DEFAULT_API_BASE_URL),
        environment.get("WECOM_CORP_ID"),
        environment.get("WECOM_APP_SECRET"),
        environment.get("WECOM_SMARTSHEET_DOC_ID"),
        environment.get("WECOM_SMARTSHEET_SHEET_ID"),
        environment.get("WECOM_SMARTSHEET_VIEW_ID"),
        environment.get("WECOM_SMARTSHEET_SOURCE_TABLE"),
        environment.get("WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE"), ZoneId.of("Asia/Shanghai"),
        WecomTransportMode.from(environment.getOrDefault("WECOM_TRANSPORT_MODE", "DIRECT")),
        new WecomRelayConfig(environment.get("WECOM_RELAY_BASE_URL"), environment.get("WECOM_RELAY_KEY_ID"),
            environment.get("WECOM_RELAY_SECRET")));
    ObjectMapper objectMapper = new ObjectMapper();
    WecomAccessTokenProvider tokenProvider = new WecomAccessTokenProvider(objectMapper, config);
    WecomSmartSheetApiClient apiClient = new WecomSmartSheetApiClient(objectMapper, config, tokenProvider);
    WecomSmartSheetFieldCatalog fieldCatalog = new WecomSmartSheetFieldCatalog(apiClient, config);
    WecomSmartSheetRecordClient recordClient = new WecomSmartSheetRecordClient(
        config, apiClient, fieldCatalog, new WecomSmartSheetValueCodec(config));
    return new WecomSmartSheetLiveAcceptanceService(
        config, apiClient, fieldCatalog, recordClient, ACCEPTANCE_TIMEOUT, tokenSuffixSupplier);
  }
}
