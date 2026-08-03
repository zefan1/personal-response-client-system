package com.privateflow.modules.customer.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class RecognitionCustomerMigrationTest {

  @Test
  void migrationAllowsRecognitionCustomersWithoutPhoneNumbers() throws Exception {
    try (InputStream stream = getClass().getClassLoader()
        .getResourceAsStream("db/migration/V87__allow_phone_less_recognition_customers.sql")) {
      assertThat(stream).isNotNull();
      String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          .toLowerCase(Locale.ROOT)
          .replaceAll("\\s+", " ");

      assertThat(sql).contains("alter table customers modify phone varchar(20) null");
      assertThat(sql).doesNotContain("drop index idx_phone");
    }
  }
}
