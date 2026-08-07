package com.privateflow.modules.tablewrite.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

import java.time.ZoneId;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
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
  void missingAllRequiredValuesNamesEveryEnvironmentVariableWithoutLeakingValues() {
    WecomSmartSheetConfig config = new WecomSmartSheetConfig(
        "https://not-leaked.example", " ", " ", " ", " ", " ", " ", " ",
        ZoneId.of("Asia/Shanghai"));

    assertThatIllegalStateException()
        .isThrownBy(config::requireConfigured)
        .withMessageContaining("WECOM_CORP_ID")
        .withMessageContaining("WECOM_APP_SECRET")
        .withMessageContaining("WECOM_SMARTSHEET_DOC_ID")
        .withMessageContaining("WECOM_SMARTSHEET_SHEET_ID")
        .withMessageContaining("WECOM_SMARTSHEET_VIEW_ID")
        .withMessageContaining("WECOM_SMARTSHEET_SOURCE_TABLE")
        .withMessageContaining("WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE")
        .satisfies(error -> assertThat(error.getMessage()).doesNotContain("not-leaked.example"));
  }

  @Test
  void applicationCredentialsCanBeValidatedBeforeATargetDocumentExists() {
    WecomSmartSheetConfig config = new WecomSmartSheetConfig(
        "https://not-leaked.example", " corp-1 ", " app-secret-value ", "", "", "", "", "",
        ZoneId.of("Asia/Shanghai"));

    config.requireApplicationCredentials();

    assertThat(config.corpId()).isEqualTo("corp-1");
    assertThat(config.appSecret()).isEqualTo("app-secret-value");
  }

  @Test
  void differentDocumentOrSourceTableIsRejectedBeforeApiCall() {
    WecomSmartSheetConfig config = configured();

    assertThatIllegalArgumentException()
        .isThrownBy(() -> config.requireTarget("other-document", "Customers"))
        .withMessageContaining("document");
    assertThatIllegalArgumentException()
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
      context.registerBean(SystemConfigRepository.class, () -> mock(SystemConfigRepository.class));
      context.register(WecomApiEndpointProvider.class);
      context.register(WecomSmartSheetConfig.class);
      context.refresh();

      assertThat(context.getBean(WecomSmartSheetConfig.class).documentId()).isEqualTo("document-1");
    }
  }

  @Test
  void explicitValuesAreTrimmedAndBaseUrlTrailingSlashesAreRemoved() {
    WecomSmartSheetConfig config = new WecomSmartSheetConfig(
        " https://qyapi.weixin.qq.com/// ", " corp-1 ", " app-secret-value ", " document-1 ",
        " sheet-1 ", " view-1 ", " Customers ", " Customer ID ", ZoneId.of("Asia/Shanghai"));

    assertThat(config.apiBaseUrl()).isEqualTo("https://qyapi.weixin.qq.com");
    assertThat(config.corpId()).isEqualTo("corp-1");
    assertThat(config.appSecret()).isEqualTo("app-secret-value");
    assertThat(config.documentId()).isEqualTo("document-1");
    assertThat(config.sheetId()).isEqualTo("sheet-1");
    assertThat(config.viewId()).isEqualTo("view-1");
    assertThat(config.sourceTable()).isEqualTo("Customers");
    assertThat(config.uniqueFieldTitle()).isEqualTo("Customer ID");
    assertThat(config.zoneId()).isEqualTo(ZoneId.of("Asia/Shanghai"));
  }

  private WecomSmartSheetConfig configured() {
    return new WecomSmartSheetConfig(
        "https://qyapi.weixin.qq.com/", "corp-1", "app-secret-value", "document-1", "sheet-1",
        "view-1", "Customers", "Customer ID", ZoneId.of("Asia/Shanghai"));
  }
}
