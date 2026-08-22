package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.config.ConfigAdminService;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetApiClient;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetFieldCatalog;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SmartSheetConnectionServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void pastedBrowserUrlUsesExistingApiDocumentAndDiscoversFields() throws Exception {
    WecomSmartSheetApiClient client = mock(WecomSmartSheetApiClient.class);
    ConfigAdminService configs = mock(ConfigAdminService.class);
    WecomSmartSheetConfig smartSheetConfig = new WecomSmartSheetConfig(
        "https://qyapi.weixin.qq.com", "corp", "secret", "doc-1", "", "", "", "");
    SmartSheetConnectionService service = new SmartSheetConnectionService(
        smartSheetConfig, new AuxiliarySmartSheetTargets(), client, configs);
    when(client.postWithApplicationCredentials(eq("get_sheet"), any(), any(Duration.class)))
        .thenReturn(json("{\"sheet_list\":[{\"sheet_id\":\"sheet-1\",\"title\":\"customer\"}]}"));
    when(client.postWithApplicationCredentials(eq("get_views"), any(), any(Duration.class)))
        .thenReturn(json("{\"views\":[{\"view_id\":\"view-1\",\"view_name\":\"default\"}]}"));
    when(client.postWithApplicationCredentials(eq("get_fields"), any(), any(Duration.class)))
        .thenReturn(json("{\"fields\":[{\"field_id\":\"f-1\",\"field_title\":\"\\u8054\\u7cfb\\u65b9\\u5f0f\"}]}"));

    SmartSheetConnectionResult result = service.verifyAndSave(new SmartSheetConnectionRequest(
        "https://doc.weixin.qq.com/smartsheet/s3_document?tab=sheet-1&viewId=view-1"));

    assertThat(result.connected()).isTrue();
    assertThat(result.documentId()).isEqualTo("doc-1");
    assertThat(result.sheetId()).isEqualTo("sheet-1");
    assertThat(result.viewId()).isEqualTo("view-1");
    assertThat(result.uniqueFieldTitle()).isEqualTo("\u8054\u7cfb\u65b9\u5f0f");
  }

  @Test
  void savesUniqueFieldWhenTheApiReturnsTitleVariantInsteadOfFieldTitle() throws Exception {
    WecomSmartSheetApiClient client = mock(WecomSmartSheetApiClient.class);
    ConfigAdminService configs = mock(ConfigAdminService.class);
    WecomSmartSheetConfig smartSheetConfig = new WecomSmartSheetConfig(
        "https://qyapi.weixin.qq.com", "corp", "secret", "doc-1", "", "", "", "");
    SmartSheetConnectionService service = new SmartSheetConnectionService(
        smartSheetConfig, new AuxiliarySmartSheetTargets(), client, configs);
    when(client.postWithApplicationCredentials(eq("get_sheet"), any(), any(Duration.class)))
        .thenReturn(json("{\"sheet_list\":[{\"sheet_id\":\"sheet-1\",\"title\":\"customer\"}]}"));
    when(client.postWithApplicationCredentials(eq("get_views"), any(), any(Duration.class)))
        .thenReturn(json("{\"views\":[{\"view_id\":\"view-1\"}]}"));
    when(client.postWithApplicationCredentials(eq("get_fields"), any(), any(Duration.class)))
        .thenReturn(json("{\"fields\":[{\"field_id\":\"f-1\",\"title\":\"手机号码\"}]}"));

    SmartSheetConnectionResult result = service.verifyAndSave(new SmartSheetConnectionRequest(
        "https://doc.weixin.qq.com/smartsheet/s3_document?tab=sheet-1"));

    assertThat(result.connected()).isTrue();
    assertThat(result.uniqueFieldTitle()).isEqualTo("手机号码");
  }

  @Test
  void invalidatesFieldDirectoryAfterConnectionIsSaved() throws Exception {
    WecomSmartSheetApiClient client = mock(WecomSmartSheetApiClient.class);
    ConfigAdminService configs = mock(ConfigAdminService.class);
    WecomSmartSheetFieldCatalog catalog = mock(WecomSmartSheetFieldCatalog.class);
    WecomSmartSheetConfig smartSheetConfig = new WecomSmartSheetConfig(
        "https://qyapi.weixin.qq.com", "corp", "secret", "doc-1", "", "", "", "");
    SmartSheetConnectionService service = new SmartSheetConnectionService(
        smartSheetConfig, new AuxiliarySmartSheetTargets(), client, configs, null, catalog);
    when(client.postWithApplicationCredentials(eq("get_sheet"), any(), any(Duration.class)))
        .thenReturn(json("{\"sheet_list\":[{\"sheet_id\":\"sheet-1\"}]}"));
    when(client.postWithApplicationCredentials(eq("get_views"), any(), any(Duration.class)))
        .thenReturn(json("{\"views\":[{\"view_id\":\"view-1\"}]}"));
    when(client.postWithApplicationCredentials(eq("get_fields"), any(), any(Duration.class)))
        .thenReturn(json("{\"fields\":[{\"field_id\":\"f-1\",\"field_title\":\"手机号码\"}]}"));

    service.verifyAndSave(new SmartSheetConnectionRequest(
        "https://doc.weixin.qq.com/smartsheet/s3_document?tab=sheet-1"));

    org.mockito.Mockito.verify(catalog).invalidate();
  }

  private JsonNode json(String value) throws Exception {
    return mapper.readTree(value);
  }
}
