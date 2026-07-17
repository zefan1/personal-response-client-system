package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerCsvWriterTest {

  @Test
  void writesUtf8CsvWithEscapingFormulaProtectionAndTagCodes() {
    CustomerAdminListItem item = new CustomerAdminListItem(
        1L, "13800000001", "=Alice", "企微\"来源", "GENERAL", "keeper-1", "万江店", "产后修复",
        "待跟进", "MEDIUM", null, null, null, null, null, null, "customers", null,
        List.of(new CustomerTagSummary(7L, "body_concerns", "身体关注", 101L, "DIASTASIS", "腹直肌分离")));

    byte[] csv = new CustomerCsvWriter().write(List.of(item));
    String text = new String(csv, StandardCharsets.UTF_8);

    assertThat(text).startsWith("\uFEFF");
    assertThat(text).contains("客户ID");
    assertThat(text).contains("\"'=Alice\"").doesNotContain("\"=Alice\"");
    assertThat(text).contains("企微\"\"来源");
    assertThat(text).contains("身体关注:腹直肌分离[DIASTASIS]");
  }
}
