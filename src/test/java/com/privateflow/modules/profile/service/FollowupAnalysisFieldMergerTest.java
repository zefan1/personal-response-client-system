package com.privateflow.modules.profile.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.admin.CustomerStageOptionService;
import com.privateflow.modules.llm.FollowupAnalysisPayload;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FollowupAnalysisFieldMergerTest {

  private final FollowupAnalysisFieldMerger merger = new FollowupAnalysisFieldMerger();

  @Test
  void mergesTheAnalysisWhileAppendingHistoryAndFillingTheNextTrackingSlot() {
    Customer customer = existingCustomer();
    FollowupAnalysisPayload payload = new FollowupAnalysisPayload(
        "客户重视安全感，先讲清评估流程",
        "腹直肌分离、腰痛",
        "产后6个月，顺产，关注腹直肌和腰痛",
        "2026-08-01：客户明确周一上午可联系",
        "高意向",
        "确认周一到店评估时间",
        "2026-08-03T10:00",
        "客户明确周一上午可联系");

    Map<String, Object> fields = merger.merge(customer, payload);

    assertThat(fields)
        .containsEntry("internalNote", "客户重视安全感，先讲清评估流程")
        .containsEntry("bodyConcerns", "腹直肌分离、腰痛")
        .containsEntry("customerProfileSummary", "产后6个月，顺产，关注腹直肌和腰痛")
        .containsEntry("followupNotes", "2026-07-31：客户首次咨询\n2026-08-01：客户明确周一上午可联系")
        .containsEntry("customerStage", "高意向")
        .containsEntry("nextFollowupDir", "确认周一到店评估时间")
        .containsEntry("nextFollowupAt", "2026-08-03T10:00")
        .containsEntry("secondTrackingCapture", "客户明确周一上午可联系")
        .doesNotContainKeys("firstTrackingCapture", "thirdTrackingCapture", "lastFollowupAt");
  }

  @Test
  void keepsExistingValuesAndDoesNotDuplicateHistoryOrTrackingDuringRetry() {
    Customer customer = existingCustomer();
    customer.setSecondTrackingCapture("客户明确周一上午可联系");
    customer.setFollowupNotes("2026-07-31：客户首次咨询\n2026-08-01：客户明确周一上午可联系");
    FollowupAnalysisPayload payload = new FollowupAnalysisPayload(
        "", "", "", "2026-08-01：客户明确周一上午可联系", "", "", null,
        "客户明确周一上午可联系");

    Map<String, Object> fields = merger.merge(customer, payload);

    assertThat(fields)
        .containsEntry("followupNotes", customer.getFollowupNotes())
        .doesNotContainKeys(
            "internalNote", "bodyConcerns", "customerProfileSummary", "customerStage",
            "nextFollowupDir", "nextFollowupAt", "firstTrackingCapture",
            "secondTrackingCapture", "thirdTrackingCapture");
  }

  @Test
  void skipsAnAiStageThatIsNotInTheCurrentWecomOptionsWithoutDroppingOtherFields() {
    CustomerStageOptionService stageOptions = mock(CustomerStageOptionService.class);
    when(stageOptions.normalizeForCustomer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("AI自定义阶段")))
        .thenThrow(new IllegalArgumentException("客户阶段不在当前企业微信选项中"));
    FollowupAnalysisFieldMerger configuredMerger = new FollowupAnalysisFieldMerger(stageOptions);

    Map<String, Object> fields = configuredMerger.merge(existingCustomer(), new FollowupAnalysisPayload(
        "客户重视安全感", "", "", "已完成首次沟通", "AI自定义阶段", "继续跟进", null, ""));

    assertThat(fields)
        .containsEntry("internalNote", "客户重视安全感")
        .containsEntry("followupNotes", "2026-07-31：客户首次咨询\n已完成首次沟通")
        .containsEntry("nextFollowupDir", "继续跟进")
        .doesNotContainKey("customerStage");
  }

  private Customer existingCustomer() {
    Customer customer = new Customer();
    customer.setBodyConcerns("腹直肌分离");
    customer.setInternalNote("旧提醒");
    customer.setCustomerProfileSummary("旧档案");
    customer.setFollowupNotes("2026-07-31：客户首次咨询");
    customer.setCustomerStage("待联系");
    customer.setFirstTrackingCapture("首次明确关注恢复周期");
    return customer;
  }
}
