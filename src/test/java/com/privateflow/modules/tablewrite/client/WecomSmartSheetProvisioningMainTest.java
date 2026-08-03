package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class WecomSmartSheetProvisioningMainTest {

  @Test
  void provisioningResultUsesAnAsciiSafeEnvelopeForChineseFieldTitles() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    String output = WecomSmartSheetProvisioningMain.encodedResult(objectMapper,
        new WecomSmartSheetProvisioningService.ProvisionedSheet(
            "doc-1", "https://doc.example/1", "sheet-1", "view-1", "sheet-1", "客户编号"));

    assertThat(output).startsWith("WECOM_SMARTSHEET_RESULT_BASE64=")
        .matches("WECOM_SMARTSHEET_RESULT_BASE64=[A-Za-z0-9+/=]+");
    String encoded = output.substring(output.indexOf('=') + 1);
    JsonNode decoded = objectMapper.readTree(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
    assertThat(decoded.path("uniqueFieldTitle").asText()).isEqualTo("客户编号");
  }
}
