package com.privateflow.modules.tablewrite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.privateflow.modules.customer.admin.DatasourceAdminRepository;
import com.privateflow.modules.customer.sync.SheetSource;
import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.tablewrite.TableWriteErrorCodes;
import com.privateflow.modules.tablewrite.TableWriteException;
import com.privateflow.modules.tablewrite.client.WecomTableClient;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRepository;

class NewCustomerRowCreatorTest {

  private final DatasourceAdminRepository datasourceRepository = mock(DatasourceAdminRepository.class);
  private final NewCustomerRowCreator creator = new NewCustomerRowCreator(
      mock(WecomTableClient.class),
      mock(TableConfigProvider.class),
      mock(TableFieldMappingResolver.class),
      mock(com.privateflow.modules.customer.infra.CustomerRepository.class),
      datasourceRepository,
      mock(ApplicationEventPublisher.class));

  @Test
  void resolvesBlankSourceTableFromEnabledDatasource() {
    when(datasourceRepository.defaultWriteSource()).thenReturn(Optional.of(new SheetSource(7L, "sheet-1", "私域客资管理表")));

    assertThat(creator.resolveSourceTable(" ")).isEqualTo("私域客资管理表");
  }

  @Test
  void rejectsBlankSourceTableWhenNoEnabledDatasourceExists() {
    when(datasourceRepository.defaultWriteSource()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> creator.resolveSourceTable(null))
        .isInstanceOf(TableWriteException.class)
        .extracting("errorCode")
        .isEqualTo(TableWriteErrorCodes.CONFIG_MISSING);
  }

  @Test
  void newCustomerFieldsKeepAllSupportedSmartSheetValuesWithoutRecentFollowupTime() {
    CustomerMessageSentEvent event = new CustomerMessageSentEvent(
        "13800000000",
        "小倩",
        true,
        "th1zyU",
        "XIAN_SUO",
        "已发送到店提醒",
        java.util.List.of(),
        "已发送到店提醒",
        "确认到店",
        new CustomerMessageSentEvent.FollowupSuggestPayload("2026-08-01T10:00:00", "确认到店"),
        false,
        "keeper");

    java.util.Map<String, Object> fields = creator.newCustomerFields(event);

    assertThat(fields)
        .containsEntry("phone", "13800000000")
        .containsEntry("nickname", "小倩")
        .containsEntry("leadType", "XIAN_SUO")
        .containsEntry("customerStage", "待联系")
        .containsEntry("followupNotes", "已发送到店提醒")
        .containsEntry("nextFollowupAt", "2026-08-01T10:00:00")
        .containsEntry("nextFollowupDir", "确认到店")
        .doesNotContainKey("lastFollowupAt");
  }

  @Test
  void newCustomerFieldsNeverUseTheCustomerFacingReplyWhenConversationSummaryIsBlank() {
    CustomerMessageSentEvent event = new CustomerMessageSentEvent(
        "13800000000",
        "小倩",
        true,
        "th1zyU",
        "XIAN_SUO",
        "",
        java.util.List.of(),
        "实际发送给客户的话术",
        "确认到店",
        null,
        false,
        "keeper");

    java.util.Map<String, Object> fields = creator.newCustomerFields(event);

    assertThat(fields).doesNotContainKey("followupNotes");
  }

  @Test
  void phoneAssignmentSyncUsesOnlyPhoneAndNickname() {
    Customer customer = new Customer();
    customer.setPhone("13434567622");
    customer.setNickname("少花");
    customer.setLeadType("XIAN_SUO");
    customer.setCustomerStage("legacy stage outside table options");
    customer.setSourceChannel("legacy source outside table options");

    java.util.Map<String, Object> fields = creator.newCustomerFields(customer);

    assertThat(fields).containsOnly(
        java.util.Map.entry("phone", "13434567622"),
        java.util.Map.entry("nickname", "少花"));
  }

  @Test
  void newCustomerFieldsIncludeTheSharedStructuredAnalysis() {
    java.util.Map<String, Object> analysisFields = java.util.Map.of(
        "internalNote", "先说明评估流程",
        "customerProfileSummary", "产后6个月，关注腹直肌",
        "followupNotes", "2026-08-01：约定周一联系");
    CustomerMessageSentEvent event = new CustomerMessageSentEvent(
        "13800000000", "小倩", true, "th1zyU", "XIAN_SUO", "", java.util.List.of(),
        "员工回复", "NEXT", null, false, analysisFields, "keeper");

    java.util.Map<String, Object> fields = creator.newCustomerFields(event);

    assertThat(fields).containsAllEntriesOf(analysisFields).doesNotContainKey("lastFollowupAt");
  }

  @Test
  void queuedCreatePersistsAllStructuredAnalysisFieldsLocally() {
    CustomerRepository customerRepository = mock(CustomerRepository.class);
    NewCustomerRowCreator localCreator = new NewCustomerRowCreator(
        mock(WecomTableClient.class),
        mock(TableConfigProvider.class),
        mock(TableFieldMappingResolver.class),
        customerRepository,
        datasourceRepository,
        mock(ApplicationEventPublisher.class));
    java.util.Map<String, Object> fields = new java.util.LinkedHashMap<>();
    fields.put("nickname", "小倩");
    fields.put("leadType", "XIAN_SUO");
    fields.put("customerStage", "高意向");
    fields.put("bodyConcerns", "腹直肌分离、腰痛");
    fields.put("internalNote", "先说明评估流程");
    fields.put("customerProfileSummary", "产后6个月，关注腹直肌和腰痛");
    fields.put("followupNotes", "2026-08-01：约定周一联系");
    fields.put("nextFollowupAt", "2026-08-03T10:00");
    fields.put("nextFollowupDir", "确认到店");
    fields.put("firstTrackingCapture", "首次关注恢复周期");
    fields.put("secondTrackingCapture", "周一上午可联系");
    fields.put("thirdTrackingCapture", "希望先了解评估流程");

    localCreator.insertCustomerAfterQueuedCreate("13800000000", "th1zyU", "row-1", fields);

    ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
    verify(customerRepository).upsert(captor.capture());
    Customer saved = captor.getValue();
    assertThat(saved.getCustomerStage()).isEqualTo("高意向");
    assertThat(saved.getBodyConcerns()).isEqualTo("腹直肌分离、腰痛");
    assertThat(saved.getInternalNote()).isEqualTo("先说明评估流程");
    assertThat(saved.getCustomerProfileSummary()).isEqualTo("产后6个月，关注腹直肌和腰痛");
    assertThat(saved.getNextFollowupAt()).isEqualTo(java.time.LocalDateTime.of(2026, 8, 3, 10, 0));
    assertThat(saved.getFirstTrackingCapture()).isEqualTo("首次关注恢复周期");
    assertThat(saved.getSecondTrackingCapture()).isEqualTo("周一上午可联系");
    assertThat(saved.getThirdTrackingCapture()).isEqualTo("希望先了解评估流程");
  }
}
