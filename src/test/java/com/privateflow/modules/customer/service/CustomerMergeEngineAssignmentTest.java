package com.privateflow.modules.customer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.privateflow.modules.customer.Customer;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CustomerMergeEngineAssignmentTest {

  @Test
  void assignmentRefreshesOwnershipAndAttributionWithoutOverwritingFollowupFacts() {
    Customer existing = new Customer();
    existing.setPhone("13800000000");
    existing.setAssignedKeeper("旧管家");
    existing.setAssignedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
    existing.setIntendedStore("旧门店");
    existing.setFollowupNotes("已确认腰痛");
    existing.setSourceTable("th1zyU");
    existing.setSourceRowId("master-row-1");

    Customer incoming = new Customer();
    incoming.setPhone("13800000000");
    incoming.setSourceTable("ASSIGNMENT:q979lj");
    incoming.setAssignedKeeper("新管家");
    incoming.setAssignedAt(LocalDateTime.of(2026, 8, 16, 9, 30));
    incoming.setIntendedStore("新门店");
    incoming.setPurchasedProject("完整购买产品文本");

    Customer merged = new CustomerMergeEngine().merge(incoming, existing);

    assertThat(merged.getAssignedKeeper()).isEqualTo("新管家");
    assertThat(merged.getAssignedAt()).isEqualTo(LocalDateTime.of(2026, 8, 16, 9, 30));
    assertThat(merged.getIntendedStore()).isEqualTo("新门店");
    assertThat(merged.getPurchasedProject()).isEqualTo("完整购买产品文本");
    assertThat(merged.getFollowupNotes()).isEqualTo("已确认腰痛");
    assertThat(merged.getSourceTable()).isEqualTo("th1zyU");
    assertThat(merged.getSourceRowId()).isEqualTo("master-row-1");
  }
}
