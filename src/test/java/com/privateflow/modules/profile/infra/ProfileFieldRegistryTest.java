package com.privateflow.modules.profile.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.privateflow.modules.customer.Customer;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProfileFieldRegistryTest {

  private final ProfileFieldRegistry registry = new ProfileFieldRegistry();

  @Test
  void supportsAllFieldsShownAsEditableInCustomerProfilePanel() {
    assertThat(registry.supportedFields()).containsAll(Set.of(
        "sourceChannel",
        "intendedStore",
        "intendedProject",
        "purchasedProject",
        "worries",
        "followupNotes",
        "bodyConcerns",
        "internalNote",
        "customerProfileSummary",
        "firstTrackingCapture",
        "secondTrackingCapture",
        "thirdTrackingCapture"));
  }

  @Test
  void readsEditableIntentFieldsFromCustomer() {
    Customer customer = new Customer();
    customer.setSourceChannel("本地测试");
    customer.setIntendedStore("上海门店");
    customer.setIntendedProject("产后修复");
    customer.setPurchasedProject("体验课");
    customer.setInternalNote("内部提醒");
    customer.setCustomerProfileSummary("客户B档案");
    customer.setFirstTrackingCapture("首次捕捉");

    assertThat(registry.readValue(customer, "sourceChannel")).isEqualTo("本地测试");
    assertThat(registry.readValue(customer, "intendedStore")).isEqualTo("上海门店");
    assertThat(registry.readValue(customer, "intendedProject")).isEqualTo("产后修复");
    assertThat(registry.readValue(customer, "purchasedProject")).isEqualTo("体验课");
    assertThat(registry.readValue(customer, "internalNote")).isEqualTo("内部提醒");
    assertThat(registry.readValue(customer, "customerProfileSummary")).isEqualTo("客户B档案");
    assertThat(registry.readValue(customer, "firstTrackingCapture")).isEqualTo("首次捕捉");
  }
}
