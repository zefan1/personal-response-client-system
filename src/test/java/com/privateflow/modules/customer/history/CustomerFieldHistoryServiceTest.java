package com.privateflow.modules.customer.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomerFieldHistoryServiceTest {

  @Test
  void snapshotKeepsEmptyCustomerFields() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("nickname", "少花");
    fields.put("bodyConcerns", null);

    CustomerFieldHistoryService.CustomerSnapshot snapshot =
        new CustomerFieldHistoryService.CustomerSnapshot(42L, fields);

    assertThat(snapshot.fields()).containsEntry("nickname", "少花").containsKey("bodyConcerns");
    assertThat(snapshot.fields().get("bodyConcerns")).isNull();
  }
}
