package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WecomSmartSheetLiveAcceptanceMainTest {

  @Test
  void createsAnAcceptanceServiceFromTheRequiredEnvironmentValues() {
    WecomSmartSheetLiveAcceptanceService service =
        WecomSmartSheetLiveAcceptanceMain.createService(Map.of(
            "WECOM_CORP_ID", "corp",
            "WECOM_APP_SECRET", "secret",
            "WECOM_SMARTSHEET_DOC_ID", "doc",
            "WECOM_SMARTSHEET_SHEET_ID", "sheet",
            "WECOM_SMARTSHEET_VIEW_ID", "view",
            "WECOM_SMARTSHEET_SOURCE_TABLE", "Customers",
            "WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE", "Unique"),
            () -> "test-run");

    assertThat(service).isNotNull();
  }
}
