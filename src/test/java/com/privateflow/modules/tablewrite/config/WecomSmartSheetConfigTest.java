package com.privateflow.modules.tablewrite.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

class WecomSmartSheetConfigTest {

  @Test
  void configuredTargetValidatesAndDoesNotExposeSecretInToString() {
    WecomSmartSheetConfig config = configured();

    config.requireTarget("document-1", "Customers");

    assertThat(config.documentId()).isEqualTo("document-1");
    assertThat(config.zoneId()).isEqualTo(ZoneId.of("Asia/Shanghai"));
    assertThat(config.toString()).doesNotContain("app-secret-value");
  }

  @Test
  void missingRequiredValuesNameEnvironmentVariablesWithoutLeakingSecret() {
    WecomSmartSheetConfig config = new WecomSmartSheetConfig(
        "https://qyapi.weixin.qq.com", " ", "app-secret-value", "", "sheet-1", "view-1",
        "Customers", "Customer ID", ZoneId.of("Asia/Shanghai"));

    assertThatIllegalStateException()
        .isThrownBy(config::requireConfigured)
        .withMessageContaining("WECOM_CORP_ID")
        .withMessageContaining("WECOM_SMARTSHEET_DOC_ID")
        .satisfies(error -> assertThat(error.getMessage()).doesNotContain("app-secret-value"));
  }

  @Test
  void differentDocumentOrSourceTableIsRejectedBeforeApiCall() {
    WecomSmartSheetConfig config = configured();

    assertThatIllegalStateException()
        .isThrownBy(() -> config.requireTarget("other-document", "Customers"))
        .withMessageContaining("document");
    assertThatIllegalStateException()
        .isThrownBy(() -> config.requireTarget("document-1", "Other customers"))
        .withMessageContaining("source table");
  }

  @Test
  void springSelectsEnvironmentConstructorWhenCreatingTheComponent() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
          "WECOM_API_BASE_URL=https://qyapi.weixin.qq.com",
          "WECOM_CORP_ID=corp-1",
          "WECOM_APP_SECRET=app-secret-value",
          "WECOM_SMARTSHEET_DOC_ID=document-1",
          "WECOM_SMARTSHEET_SHEET_ID=sheet-1",
          "WECOM_SMARTSHEET_VIEW_ID=view-1",
          "WECOM_SMARTSHEET_SOURCE_TABLE=Customers",
          "WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE=Customer ID");
      context.register(WecomSmartSheetConfig.class);
      context.refresh();

      assertThat(context.getBean(WecomSmartSheetConfig.class).documentId()).isEqualTo("document-1");
    }
  }

  private WecomSmartSheetConfig configured() {
    return new WecomSmartSheetConfig(
        "https://qyapi.weixin.qq.com/", "corp-1", "app-secret-value", "document-1", "sheet-1",
        "view-1", "Customers", "Customer ID", ZoneId.of("Asia/Shanghai"));
  }
}
