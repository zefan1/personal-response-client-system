package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/** Entrypoint for creating the isolated API acceptance Smart Sheet. */
public final class WecomSmartSheetProvisioningMain {

  private static final String DEFAULT_API_BASE_URL = "https://qyapi.weixin.qq.com";
  private static final Duration PROVISIONING_TIMEOUT = Duration.ofSeconds(60);

  private WecomSmartSheetProvisioningMain() {}

  public static void main(String[] args) {
    try {
      Map<String, String> environment = System.getenv();
      String documentName = environment.getOrDefault(
          "WECOM_SMARTSHEET_DOCUMENT_NAME", "私域辅助系统-API联调");
      ObjectMapper objectMapper = new ObjectMapper();
      WecomSmartSheetConfig config = new WecomSmartSheetConfig(
          environment.getOrDefault("WECOM_API_BASE_URL", DEFAULT_API_BASE_URL),
          environment.get("WECOM_CORP_ID"),
          environment.get("WECOM_APP_SECRET"),
          "", "", "", "", "");
      WecomAccessTokenProvider tokenProvider = new WecomAccessTokenProvider(objectMapper, config);
      WecomSmartSheetApiClient apiClient = new WecomSmartSheetApiClient(objectMapper, config, tokenProvider);
      WecomSmartSheetProvisioningService service =
          new WecomSmartSheetProvisioningService(apiClient, PROVISIONING_TIMEOUT);
      String mode = environment.getOrDefault("WECOM_SMARTSHEET_PROVISIONING_MODE", "CREATE").trim();
      Object result = switch (mode) {
        case "CREATE" -> service.createDocument(documentName);
        case "PREPARE" -> service.prepare(new WecomSmartSheetProvisioningService.CreatedDocument(
            environment.get("WECOM_SMARTSHEET_DOC_ID"),
            environment.get("WECOM_SMARTSHEET_DOCUMENT_URL")));
        default -> throw new IllegalArgumentException("Unsupported Smart Sheet provisioning mode");
      };
      System.out.println(encodedResult(objectMapper, result));
    } catch (RuntimeException | java.io.IOException exception) {
      String message = exception.getMessage();
      System.err.println("WeCom Smart Sheet provisioning failed: "
          + (message == null || message.isBlank() ? "unexpected runtime failure" : message));
      System.exit(1);
    }
  }

  static String encodedResult(ObjectMapper objectMapper, Object result) throws java.io.IOException {
    return "WECOM_SMARTSHEET_RESULT_BASE64="
        + Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(result));
  }
}
