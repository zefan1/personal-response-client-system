package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class WecomSmartSheetProvisioningServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void copiesTemplateFieldMetadataAndVerifiesTheCreatedSchema() throws Exception {
    WecomSmartSheetApiClient apiClient = mock(WecomSmartSheetApiClient.class);
    Duration timeout = Duration.ofSeconds(30);
    when(apiClient.postWithApplicationCredentials(eq("get_fields"), any(), eq(timeout)))
        .thenReturn(json("""
            {"fields":[
              {"field_id":"source-phone","field_title":"手机号码","field_type":"FIELD_TYPE_TEXT"},
              {"field_id":"source-stage","field_title":"客户阶段","field_type":"FIELD_TYPE_SINGLE_SELECT",
               "property_single_select":{"options":[{"id":"stage-a","text":"待跟进"}]}}
            ]}
            """))
        .thenReturn(json("""
            {"fields":[{"field_id":"default-field","field_title":"智能表列","field_type":"FIELD_TYPE_TEXT"}]}
            """))
        .thenReturn(json("""
            {"fields":[
              {"field_id":"new-phone","field_title":"手机号码","field_type":"FIELD_TYPE_TEXT"},
              {"field_id":"new-stage","field_title":"客户阶段","field_type":"FIELD_TYPE_SINGLE_SELECT",
               "property_single_select":{"options":[{"id":"stage-a","text":"待跟进"}]}}
            ]}
            """));
    when(apiClient.postWithApplicationCredentials(eq("create_doc"), any(), eq(timeout)))
        .thenReturn(json("{\"docid\":\"new-doc\",\"url\":\"https://doc.weixin.qq.com/new\"}"));
    when(apiClient.postWithApplicationCredentials(eq("get_sheet"), any(), eq(timeout)))
        .thenReturn(json("{\"sheet_list\":[{\"sheet_id\":\"new-sheet\",\"type\":\"smartsheet\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("get_views"), any(), eq(timeout)))
        .thenReturn(json("{\"views\":[{\"view_id\":\"new-view\",\"view_type\":\"VIEW_TYPE_GRID\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("update_fields"), any(), eq(timeout)))
        .thenReturn(json("{\"fields\":[]}"));
    when(apiClient.postWithApplicationCredentials(eq("add_fields"), any(), eq(timeout)))
        .thenReturn(json("{\"fields\":[]}"));

    WecomSmartSheetProvisioningService service = new WecomSmartSheetProvisioningService(apiClient, timeout);
    WecomSmartSheetProvisioningService.ProvisionedSheet result = service.provisionFromTemplate(
        "9月新客分配",
        new AuxiliarySmartSheetTarget("ASSIGNMENT", "old-doc", "old-sheet", "old-view", "联系方式", ""));

    assertThat(result.documentId()).isEqualTo("new-doc");
    assertThat(result.sheetId()).isEqualTo("new-sheet");
    assertThat(result.uniqueFieldTitle()).isEqualTo("手机号码");
    ArgumentCaptor<Map<String, Object>> add = requestCaptor();
    verify(apiClient).postWithApplicationCredentials(eq("add_fields"), add.capture(), eq(timeout));
    assertThat(add.getValue().toString())
        .contains("客户阶段", "FIELD_TYPE_SINGLE_SELECT", "property_single_select", "待跟进");
  }

  @Test
  void refusesToReturnAProvisionedTableWhenReadBackIsMissingAField() throws Exception {
    WecomSmartSheetApiClient apiClient = mock(WecomSmartSheetApiClient.class);
    Duration timeout = Duration.ofSeconds(30);
    when(apiClient.postWithApplicationCredentials(eq("get_fields"), any(), eq(timeout)))
        .thenReturn(json("""
            {"fields":[
              {"field_id":"source-phone","field_title":"联系方式","field_type":"FIELD_TYPE_TEXT"},
              {"field_id":"source-project","field_title":"购买项目","field_type":"FIELD_TYPE_TEXT"}
            ]}
            """))
        .thenReturn(json("{\"fields\":[{\"field_id\":\"default-field\",\"field_title\":\"智能表列\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}"))
        .thenReturn(json("{\"fields\":[{\"field_id\":\"new-phone\",\"field_title\":\"联系方式\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("create_doc"), any(), eq(timeout)))
        .thenReturn(json("{\"docid\":\"new-doc\",\"url\":\"https://doc.weixin.qq.com/new\"}"));
    when(apiClient.postWithApplicationCredentials(eq("get_sheet"), any(), eq(timeout)))
        .thenReturn(json("{\"sheet_list\":[{\"sheet_id\":\"new-sheet\",\"type\":\"smartsheet\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("get_views"), any(), eq(timeout)))
        .thenReturn(json("{\"views\":[{\"view_id\":\"new-view\",\"view_type\":\"VIEW_TYPE_GRID\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("update_fields"), any(), eq(timeout)))
        .thenReturn(json("{\"fields\":[]}"));
    when(apiClient.postWithApplicationCredentials(eq("add_fields"), any(), eq(timeout)))
        .thenReturn(json("{\"fields\":[]}"));

    WecomSmartSheetProvisioningService service = new WecomSmartSheetProvisioningService(apiClient, timeout);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.provisionFromTemplate(
        "字段缺失表",
        new AuxiliarySmartSheetTarget("ASSIGNMENT", "old-doc", "old-sheet", "old-view", "联系方式", "")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("结构校验失败")
        .hasMessageContaining("购买项目");
  }

  @Test
  void createsTheKeeperColumnAsTextAndReportsTheDocumentBeforeFieldProvisioning() throws Exception {
    WecomSmartSheetApiClient apiClient = mock(WecomSmartSheetApiClient.class);
    Duration timeout = Duration.ofSeconds(30);
    when(apiClient.postWithApplicationCredentials(eq("get_fields"), any(), eq(timeout)))
        .thenReturn(json("""
            {"fields":[
              {"field_id":"source-phone","field_title":"联系方式","field_type":"FIELD_TYPE_TEXT"},
              {"field_id":"source-keeper","field_title":"管家","field_type":"FIELD_TYPE_MEMBER"}
            ]}
            """))
        .thenReturn(json("{\"fields\":[{\"field_id\":\"default-field\",\"field_title\":\"智能表列\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}"))
        .thenReturn(json("""
            {"fields":[
              {"field_id":"new-phone","field_title":"联系方式","field_type":"FIELD_TYPE_TEXT"},
              {"field_id":"new-keeper","field_title":"管家","field_type":"FIELD_TYPE_TEXT"}
            ]}
            """));
    when(apiClient.postWithApplicationCredentials(eq("create_doc"), any(), eq(timeout)))
        .thenReturn(json("{\"docid\":\"new-doc\",\"url\":\"https://doc.weixin.qq.com/new\"}"));
    when(apiClient.postWithApplicationCredentials(eq("get_sheet"), any(), eq(timeout)))
        .thenReturn(json("{\"sheet_list\":[{\"sheet_id\":\"new-sheet\",\"type\":\"smartsheet\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("get_views"), any(), eq(timeout)))
        .thenReturn(json("{\"views\":[{\"view_id\":\"new-view\",\"view_type\":\"VIEW_TYPE_GRID\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("update_fields"), any(), eq(timeout)))
        .thenReturn(json("{\"fields\":[]}"));
    when(apiClient.postWithApplicationCredentials(eq("add_fields"), any(), eq(timeout)))
        .thenReturn(json("{\"fields\":[]}"));

    java.util.List<WecomSmartSheetProvisioningService.CreatedDocument> reported = new java.util.ArrayList<>();
    WecomSmartSheetProvisioningService service = new WecomSmartSheetProvisioningService(apiClient, timeout);
    service.provisionFromTemplate("9月新客分配",
        new AuxiliarySmartSheetTarget("ASSIGNMENT", "old-doc", "old-sheet", "old-view", "联系方式", ""),
        reported::add);

    assertThat(reported).containsExactly(new WecomSmartSheetProvisioningService.CreatedDocument(
        "new-doc", "https://doc.weixin.qq.com/new"));
    ArgumentCaptor<Map<String, Object>> add = requestCaptor();
    verify(apiClient).postWithApplicationCredentials(eq("add_fields"), add.capture(), eq(timeout));
    assertThat(add.getValue()).containsEntry("fields", java.util.List.of(Map.of(
        "field_title", "管家", "field_type", "FIELD_TYPE_TEXT")));
  }

  @Test
  void resumesAnExistingFailedTableWithoutAddingDuplicateFields() throws Exception {
    WecomSmartSheetApiClient apiClient = mock(WecomSmartSheetApiClient.class);
    Duration timeout = Duration.ofSeconds(30);
    String fields = """
        {"fields":[
          {"field_id":"keeper","field_title":"管家","field_type":"FIELD_TYPE_TEXT"},
          {"field_id":"phone","field_title":"手机号码","field_type":"FIELD_TYPE_TEXT"}
        ]}
        """;
    when(apiClient.postWithApplicationCredentials(eq("get_fields"), any(), eq(timeout)))
        .thenReturn(json(fields))
        .thenReturn(json(fields));
    when(apiClient.postWithApplicationCredentials(eq("get_sheet"), any(), eq(timeout)))
        .thenReturn(json("{\"sheet_list\":[{\"sheet_id\":\"new-sheet\",\"type\":\"smartsheet\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("get_views"), any(), eq(timeout)))
        .thenReturn(json("{\"views\":[{\"view_id\":\"new-view\",\"view_type\":\"VIEW_TYPE_GRID\"}]}"));

    WecomSmartSheetProvisioningService service = new WecomSmartSheetProvisioningService(apiClient, timeout);
    WecomSmartSheetProvisioningService.ProvisionedSheet result = service.provisionExistingFromTemplate(
        new WecomSmartSheetProvisioningService.CreatedDocument("new-doc", "https://doc.weixin.qq.com/new"),
        new AuxiliarySmartSheetTarget("ASSIGNMENT", "old-doc", "old-sheet", "old-view", "联系方式", ""));

    assertThat(result.uniqueFieldTitle()).isEqualTo("手机号码");
    verify(apiClient, never()).postWithApplicationCredentials(eq("update_fields"), any(), eq(timeout));
    verify(apiClient, never()).postWithApplicationCredentials(eq("add_fields"), any(), eq(timeout));
  }

  @Test
  void createsAnApiOwnedSmartSheetAndPreparesItsDefaultSheetForAcceptance() throws Exception {
    WecomSmartSheetApiClient apiClient = mock(WecomSmartSheetApiClient.class);
    Duration timeout = Duration.ofSeconds(30);
    when(apiClient.postWithApplicationCredentials(eq("create_doc"), any(), eq(timeout)))
        .thenReturn(json("{\"errcode\":0,\"docid\":\"doc-1\",\"url\":\"https://doc.example/1\"}"));
    when(apiClient.postWithApplicationCredentials(eq("get_sheet"), any(), eq(timeout)))
        .thenReturn(json("{\"errcode\":0,\"sheet_list\":[{\"sheet_id\":\"sheet-1\",\"title\":\"数据表\",\"is_visible\":true,\"type\":\"smartsheet\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("get_views"), any(), eq(timeout)))
        .thenReturn(json("{\"errcode\":0,\"views\":[{\"view_id\":\"view-1\",\"view_title\":\"默认视图\",\"view_type\":\"VIEW_TYPE_GRID\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("get_fields"), any(), eq(timeout)))
        .thenReturn(json("{\"errcode\":0,\"total\":2,\"fields\":[{\"field_id\":\"field-1\",\"field_title\":\"智能表列\",\"field_type\":\"FIELD_TYPE_TEXT\"},{\"field_id\":\"field-formula\",\"field_title\":\"验收公式\",\"field_type\":\"FIELD_TYPE_FORMULA\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("update_fields"), any(), eq(timeout)))
        .thenReturn(json("{\"errcode\":0,\"fields\":[{\"field_id\":\"field-1\",\"field_title\":\"联系方式\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("add_fields"), any(), eq(timeout)))
        .thenReturn(json("{\"errcode\":0,\"fields\":[]}"));

    WecomSmartSheetProvisioningService service = new WecomSmartSheetProvisioningService(apiClient, timeout);
    WecomSmartSheetProvisioningService.CreatedDocument created = service.createDocument("私域辅助系统-API联调");

    assertThat(created).isEqualTo(new WecomSmartSheetProvisioningService.CreatedDocument(
        "doc-1", "https://doc.example/1"));
    verify(apiClient, never()).postWithApplicationCredentials(eq("get_sheet"), any(), eq(timeout));

    WecomSmartSheetProvisioningService.ProvisionedSheet result = service.prepare(created);

    assertThat(result).isEqualTo(new WecomSmartSheetProvisioningService.ProvisionedSheet(
        "doc-1", "https://doc.example/1", "sheet-1", "view-1", "sheet-1", "联系方式"));

    InOrder ordered = inOrder(apiClient);
    ordered.verify(apiClient).postWithApplicationCredentials(eq("create_doc"), any(), eq(timeout));
    ordered.verify(apiClient).postWithApplicationCredentials(eq("get_sheet"), any(), eq(timeout));
    ordered.verify(apiClient).postWithApplicationCredentials(eq("get_views"), any(), eq(timeout));
    ordered.verify(apiClient).postWithApplicationCredentials(eq("get_fields"), any(), eq(timeout));
    ArgumentCaptor<Map<String, Object>> update = requestCaptor();
    ordered.verify(apiClient).postWithApplicationCredentials(eq("update_fields"), update.capture(), eq(timeout));
    assertThat(update.getValue().get("docid")).isEqualTo("doc-1");
    assertThat(update.getValue().get("sheet_id")).isEqualTo("sheet-1");
    assertThat(update.getValue().toString()).contains("联系方式", "FIELD_TYPE_TEXT", "field-1");
    ArgumentCaptor<Map<String, Object>> add = requestCaptor();
    ordered.verify(apiClient).postWithApplicationCredentials(eq("add_fields"), add.capture(), eq(timeout));
    assertThat(add.getValue().toString())
        .contains("姓名", "客资类型", "客户阶段", "购买项目", "备注", "下次跟进方向", "下次跟进时间")
        .doesNotContain("客户姓名", "手机号", "微信昵称", "客户类型", "跟进状态", "最近跟进时间");
  }

  @Test
  void resumesWithoutAddingFieldsThatAlreadyExist() throws Exception {
    WecomSmartSheetApiClient apiClient = mock(WecomSmartSheetApiClient.class);
    Duration timeout = Duration.ofSeconds(30);
    when(apiClient.postWithApplicationCredentials(eq("get_sheet"), any(), eq(timeout)))
        .thenReturn(json("{\"sheet_list\":[{\"sheet_id\":\"sheet-1\",\"type\":\"smartsheet\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("get_views"), any(), eq(timeout)))
        .thenReturn(json("{\"views\":[{\"view_id\":\"view-1\",\"view_type\":\"VIEW_TYPE_GRID\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("get_fields"), any(), eq(timeout)))
        .thenReturn(json("""
            {"fields":[
              {"field_id":"f-unique","field_title":"联系方式","field_type":"FIELD_TYPE_TEXT"},
              {"field_id":"f-name","field_title":"姓名","field_type":"FIELD_TYPE_TEXT"},
              {"field_id":"f-lead","field_title":"客资类型","field_type":"FIELD_TYPE_TEXT"},
              {"field_id":"f-stage","field_title":"客户阶段","field_type":"FIELD_TYPE_TEXT"},
              {"field_id":"f-purchased","field_title":"购买项目","field_type":"FIELD_TYPE_TEXT"},
              {"field_id":"f-note","field_title":"备注","field_type":"FIELD_TYPE_TEXT"},
              {"field_id":"f-next-dir","field_title":"下次跟进方向","field_type":"FIELD_TYPE_TEXT"},
              {"field_id":"f-next-at","field_title":"下次跟进时间","field_type":"FIELD_TYPE_TEXT"},
              {"field_id":"f-last-at","field_title":"最近跟进时间","field_type":"FIELD_TYPE_TEXT"},
              {"field_id":"f-formula","field_title":"验收公式","field_type":"FIELD_TYPE_FORMULA"}
            ]}
            """));

    WecomSmartSheetProvisioningService service = new WecomSmartSheetProvisioningService(apiClient, timeout);
    service.prepare(new WecomSmartSheetProvisioningService.CreatedDocument("doc-1", "https://doc.example/1"));

    verify(apiClient, never()).postWithApplicationCredentials(eq("update_fields"), any(), eq(timeout));
    verify(apiClient, never()).postWithApplicationCredentials(eq("add_fields"), any(), eq(timeout));
  }

  @Test
  void refusesToFinishWhenTheApiOwnedSheetHasNoFormulaField() throws Exception {
    WecomSmartSheetApiClient apiClient = mock(WecomSmartSheetApiClient.class);
    Duration timeout = Duration.ofSeconds(30);
    when(apiClient.postWithApplicationCredentials(eq("get_sheet"), any(), eq(timeout)))
        .thenReturn(json("{\"sheet_list\":[{\"sheet_id\":\"sheet-1\",\"type\":\"smartsheet\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("get_views"), any(), eq(timeout)))
        .thenReturn(json("{\"views\":[{\"view_id\":\"view-1\",\"view_type\":\"VIEW_TYPE_GRID\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("get_fields"), any(), eq(timeout)))
        .thenReturn(json("{\"fields\":[{\"field_id\":\"f-unique\",\"field_title\":\"联系方式\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}"));
    when(apiClient.postWithApplicationCredentials(eq("add_fields"), any(), eq(timeout)))
        .thenReturn(json("{\"fields\":[]}"));

    WecomSmartSheetProvisioningService service = new WecomSmartSheetProvisioningService(apiClient, timeout);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.prepare(
        new WecomSmartSheetProvisioningService.CreatedDocument("doc-1", "https://doc.example/1")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("formula");
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Map<String, Object>> requestCaptor() {
    return ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
  }

  private com.fasterxml.jackson.databind.JsonNode json(String value) throws Exception {
    return objectMapper.readTree(value);
  }
}
