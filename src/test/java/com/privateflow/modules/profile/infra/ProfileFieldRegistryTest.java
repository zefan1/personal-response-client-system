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
        "bodyConcerns"));
  }

  @Test
  void readsEditableIntentFieldsFromCustomer() {
    Customer customer = new Customer();
    customer.setSourceChannel("本地测试");
    customer.setIntendedStore("上海门店");
    customer.setIntendedProject("产后修复");
    customer.setPurchasedProject("体验课");

    assertThat(registry.readValue(customer, "sourceChannel")).isEqualTo("本地测试");
    assertThat(registry.readValue(customer, "intendedStore")).isEqualTo("上海门店");
    assertThat(registry.readValue(customer, "intendedProject")).isEqualTo("产后修复");
    assertThat(registry.readValue(customer, "purchasedProject")).isEqualTo("体验课");
  }
}
