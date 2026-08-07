package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.config.ConfigAdminService;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetApiClient;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SmartSheetConnectionServiceTest {

  private static final String DOCUMENT_ID = "doc-api-owned";
  private static final String SHEET_ID = "sheet-api-owned";
  private static final String VIEW_ID = "view-api-owned";
  private static final String DOCUMENT_URL = "https://doc.weixin.qq.com/smartsheet/doc-api-owned";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WecomSmartSheetApiClient apiClient = mock(WecomSmartSheetApiClient.class);
  private final ConfigAdminService configAdminService = mock(ConfigAdminService.class);
  private SmartSheetConnectionService service;

  @BeforeEach
  void setUp() {
    WecomSmartSheetConfig config = new WecomSmartSheetConfig(
        "https://qyapi.weixin.qq.com",
        "corp-id",
        "app-secret",
        DOCUMENT_ID,
        SHEET_ID,
        VIEW_ID,
        "customer_api_sheet",
        "联系方式");
    service = new SmartSheetConnectionService(config, apiClient, configAdminService);
  }

  @Test
  void rejectsBlankOrNonHttpLinks() {
    assertThatThrownBy(() -> service.verifyAndSave(new SmartSheetConnectionRequest("")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("完整链接");
    assertThatThrownBy(() -> service.verifyAndSave(new SmartSheetConnectionRequest("file:///tmp/table")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("完整链接");
  }

  @Test
  void rejectsTablesThatAreNotTheConfiguredApiOwnedDocument() {
    assertThatThrownBy(() -> service.verifyAndSave(new SmartSheetConnectionRequest(
        "https://doc.weixin.qq.com/smartsheet/manually-created")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("不是本系统通过企业微信 API 创建并纳入的数据表");
  }

  @Test
  void rejectsConfiguredDocumentsWithoutTheExpectedChildSheet() throws Exception {
    when(apiClient.post(eq("get_sheet"), any(), any(Duration.class)))
        .thenReturn(json("{\"sheet_list\":[{\"sheet_id\":\"other-sheet\",\"sheet_name\":\"其他表\"}]}"));

    assertThatThrownBy(() -> service.verifyAndSave(new SmartSheetConnectionRequest(DOCUMENT_URL)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("没有找到本系统创建的子表");
  }

  @Test
  void rejectsConfiguredDocumentsWithoutTheExpectedView() throws Exception {
    when(apiClient.post(eq("get_sheet"), any(), any(Duration.class)))
        .thenReturn(sheetResponse(SHEET_ID, "客户资料表"));
    when(apiClient.post(eq("get_views"), any(), any(Duration.class)))
        .thenReturn(json("{\"views\":[{\"view_id\":\"other-view\",\"view_name\":\"其他视图\"}]}"));

    assertThatThrownBy(() -> service.verifyAndSave(new SmartSheetConnectionRequest(DOCUMENT_URL)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("没有找到本系统创建的表格视图");
  }

  @Test
  void verifiesAndStoresTheConfiguredApiOwnedDocumentUrl() throws Exception {
    when(apiClient.post(eq("get_sheet"), any(), any(Duration.class)))
        .thenReturn(sheetResponse(SHEET_ID, "客户资料表"));
    when(apiClient.post(eq("get_views"), any(), any(Duration.class)))
        .thenReturn(json("{\"views\":[{\"view_id\":\"" + VIEW_ID + "\",\"view_name\":\"默认视图\"}]}"));

    SmartSheetConnectionResult result = service.verifyAndSave(new SmartSheetConnectionRequest(DOCUMENT_URL));

    assertThat(result.connected()).isTrue();
    assertThat(result.tableName()).isEqualTo("客户资料表");
    assertThat(result.documentId()).isEqualTo(DOCUMENT_ID);
    assertThat(result.sheetId()).isEqualTo(SHEET_ID);
    assertThat(result.viewId()).isEqualTo(VIEW_ID);
    assertThat(result.documentUrl()).isEqualTo(DOCUMENT_URL);
    verify(configAdminService).update("table.document_url", Map.of("value", DOCUMENT_URL));
  }

  private JsonNode sheetResponse(String sheetId, String sheetName) throws Exception {
    return json("{\"sheet_list\":[{\"sheet_id\":\"" + sheetId + "\",\"sheet_name\":\"" + sheetName + "\"}]}");
  }

  private JsonNode json(String value) throws Exception {
    return objectMapper.readTree(value);
  }
}
