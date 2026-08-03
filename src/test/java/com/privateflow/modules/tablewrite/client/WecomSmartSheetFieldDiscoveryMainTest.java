package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WecomSmartSheetFieldDiscoveryMainTest {

  @Test
  void allowsEnoughTimeForTransientConnectionRetries() {
    assertThat(WecomSmartSheetFieldDiscoveryMain.discoveryTimeout()).isEqualTo(java.time.Duration.ofSeconds(60));
  }

  @Test
  void createsAFieldCatalogFromConnectionValuesWithoutBusinessFieldChoices() {
    WecomSmartSheetFieldCatalog catalog = WecomSmartSheetFieldDiscoveryMain.createFieldCatalog(Map.of(
        "WECOM_CORP_ID", "corp",
        "WECOM_APP_SECRET", "secret",
        "WECOM_SMARTSHEET_DOC_ID", "doc",
        "WECOM_SMARTSHEET_SHEET_ID", "sheet",
        "WECOM_SMARTSHEET_VIEW_ID", "view"));

    assertThat(catalog).isNotNull();
  }
}
