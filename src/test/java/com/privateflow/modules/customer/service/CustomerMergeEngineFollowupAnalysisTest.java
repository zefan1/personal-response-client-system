package com.privateflow.modules.customer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.privateflow.modules.customer.Customer;
import org.junit.jupiter.api.Test;

class CustomerMergeEngineFollowupAnalysisTest {

  @Test
  void privateDomainSheetMergeKeepsTheNewAnalysisFields() {
    Customer existing = new Customer();
    existing.setPhone("13800000000");
    existing.setInternalNote("旧提醒");
    existing.setCustomerProfileSummary("旧档案");
    existing.setFirstTrackingCapture("首次捕捉");
    Customer incoming = new Customer();
    incoming.setPhone("13800000000");
    incoming.setSourceTable("私域客资管理表");
    incoming.setInternalNote("新提醒");
    incoming.setCustomerProfileSummary("新档案");
    incoming.setSecondTrackingCapture("第二次捕捉");

    Customer merged = new CustomerMergeEngine().merge(incoming, existing);

    assertThat(merged.getInternalNote()).isEqualTo("新提醒");
    assertThat(merged.getCustomerProfileSummary()).isEqualTo("新档案");
    assertThat(merged.getFirstTrackingCapture()).isEqualTo("首次捕捉");
    assertThat(merged.getSecondTrackingCapture()).isEqualTo("第二次捕捉");
  }

  @Test
  void managedSheetIdMergeUpdatesCustomerStage() {
    Customer existing = new Customer();
    existing.setPhone("13800000000");
    existing.setCustomerStage("待联系");

    Customer incoming = new Customer();
    incoming.setPhone("13800000000");
    incoming.setSourceTable("th1zyU");
    incoming.setCustomerStage("无效线索");

    Customer merged = new CustomerMergeEngine().merge(incoming, existing);

    assertThat(merged.getCustomerStage()).isEqualTo("无效线索");
  }
}
