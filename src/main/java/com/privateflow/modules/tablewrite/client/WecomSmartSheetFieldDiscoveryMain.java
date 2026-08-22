package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.tablewrite.config.WecomRelayConfig;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import com.privateflow.modules.tablewrite.config.WecomTransportMode;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;

/** Lists viable unique-field choices before the controlled provider acceptance writes a test record. */
public final class WecomSmartSheetFieldDiscoveryMain {

  private static final String DEFAULT_API_BASE_URL = "https://qyapi.weixin.qq.com";
  private static final Duration DISCOVERY_TIMEOUT = Duration.ofSeconds(60);

  private WecomSmartSheetFieldDiscoveryMain() {}

  public static void main(String[] args) {
    try {
      Map<String, WecomSmartSheetField> fields = createFieldCatalog(System.getenv()).visibleFields(discoveryTimeout());
      long candidates = fields.values().stream()
          .filter(WecomSmartSheetField::writable)
          .filter(WecomSmartSheetFieldDiscoveryMain::supportsUniqueTestValue)
          .peek(field -> System.out.printf("%s [%s]%n", field.title(), field.type()))
          .count();
      if (candidates == 0) {
        throw new IllegalStateException("No text, phone, or email field is available for duplicate protection");
      }
    } catch (RuntimeException exception) {
      String message = exception.getMessage();
      System.err.println("WeCom Smart Sheet field discovery failed: "
          + (message == null || message.isBlank() ? "unexpected runtime failure" : message));
      System.exit(1);
    }
  }

  static WecomSmartSheetFieldCatalog createFieldCatalog(Map<String, String> environment) {
    WecomSmartSheetConfig config = new WecomSmartSheetConfig(
        environment.getOrDefault("WECOM_API_BASE_URL", DEFAULT_API_BASE_URL),
        environment.get("WECOM_CORP_ID"),
        environment.get("WECOM_APP_SECRET"),
        environment.get("WECOM_SMARTSHEET_DOC_ID"),
        environment.get("WECOM_SMARTSHEET_SHEET_ID"),
        environment.get("WECOM_SMARTSHEET_VIEW_ID"),
        "discovery",
        "discovery",
        ZoneId.of("Asia/Shanghai"),
        WecomTransportMode.from(environment.getOrDefault("WECOM_TRANSPORT_MODE", "DIRECT")),
        new WecomRelayConfig(environment.get("WECOM_RELAY_BASE_URL"), environment.get("WECOM_RELAY_KEY_ID"),
            environment.get("WECOM_RELAY_SECRET")));
    ObjectMapper objectMapper = new ObjectMapper();
    WecomAccessTokenProvider tokenProvider = new WecomAccessTokenProvider(objectMapper, config);
    WecomSmartSheetApiClient apiClient = new WecomSmartSheetApiClient(objectMapper, config, tokenProvider);
    return new WecomSmartSheetFieldCatalog(apiClient, config);
  }

  static Duration discoveryTimeout() {
    return DISCOVERY_TIMEOUT;
  }

  private static boolean supportsUniqueTestValue(WecomSmartSheetField field) {
    return switch (field.type()) {
      case "FIELD_TYPE_TEXT", "FIELD_TYPE_PHONE_NUMBER", "FIELD_TYPE_EMAIL" -> true;
      default -> false;
    };
  }
}
