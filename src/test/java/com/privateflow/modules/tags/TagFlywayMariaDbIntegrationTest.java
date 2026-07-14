package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TagFlywayMariaDbIntegrationTest {

  @Test
  @EnabledIfEnvironmentVariable(named = "TAG_FLYWAY_IT", matches = "true")
  void migratesFreshMariaDbAndSecondRunIsNoOp() throws Exception {
    String url = required("TAG_FLYWAY_URL");
    String username = required("TAG_FLYWAY_USERNAME");
    String password = System.getenv().getOrDefault("TAG_FLYWAY_PASSWORD", "");
    Flyway flyway = Flyway.configure()
        .dataSource(url, username, password)
        .locations("classpath:db/migration")
        .load();

    MigrateResult first = flyway.migrate();
    MigrateResult second = flyway.migrate();

    assertThat(first.targetSchemaVersion).isEqualTo("68");
    assertThat(first.migrationsExecuted).isGreaterThan(0);
    assertThat(second.migrationsExecuted).isZero();
    try (var connection = DriverManager.getConnection(url, username, password);
         var statement = connection.createStatement()) {
      try (var result = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE version='68' AND success=1")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(1);
      }
      try (var result = statement.executeQuery("SELECT COUNT(*) FROM tag_values WHERE meaning='' OR synonyms_json='[]'")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isZero();
      }
      try (var result = statement.executeQuery("SELECT COUNT(*) FROM personality_tags WHERE enabled=0 AND migration_status='MAPPED' AND canonical_tag_value_id IS NOT NULL")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(3);
      }
      try (var result = statement.executeQuery("SELECT COUNT(*) FROM tag_legacy_value_mappings WHERE source_type='PERSONALITY_TAGS' AND mapping_status='MAPPED'")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(3);
      }
    }

    DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.update("INSERT INTO customers (phone, body_concerns) VALUES ('19900006888', '腹直肌分离,漏尿')");
    LegacyCustomerTagSynchronizer synchronizer = new LegacyCustomerTagSynchronizer(jdbcTemplate);
    synchronizer.synchronize("19900006888", Map.of("bodyConcerns", "腹直肌分离,漏尿"));
    assertThat(jdbcTemplate.queryForObject(
        "SELECT body_concerns FROM customers WHERE phone='19900006888'", String.class))
        .isEqualTo("DIASTASIS_RECTI,URINE_LEAKAGE");
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM customer_tag_assignments a
        JOIN customers c ON c.id=a.customer_id
        WHERE c.phone='19900006888' AND a.is_active=1
        """, Integer.class)).isEqualTo(2);

    jdbcTemplate.update("UPDATE customers SET body_concerns='腹直肌分离,未知关注' WHERE phone='19900006888'");
    synchronizer.synchronize("19900006888", Map.of("bodyConcerns", "腹直肌分离,未知关注"));
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM unmatched_legacy_tag_values u
        JOIN customers c ON c.id=u.customer_id
        WHERE c.phone='19900006888' AND u.legacy_field='bodyConcerns' AND u.status='PENDING'
        """, Integer.class)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM customer_tag_assignments a
        JOIN customers c ON c.id=a.customer_id
        WHERE c.phone='19900006888' AND a.is_active=1
        """, Integer.class)).isEqualTo(1);

    TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
      jdbcTemplate.update("UPDATE customers SET body_concerns='漏尿' WHERE phone='19900006888'");
      synchronizer.synchronize("19900006888", Map.of("bodyConcerns", "漏尿"));
      throw new IllegalStateException("force rollback");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT body_concerns FROM customers WHERE phone='19900006888'", String.class))
        .isEqualTo("腹直肌分离,未知关注");
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM customer_tag_assignments a
        JOIN customers c ON c.id=a.customer_id
        JOIN tag_values v ON v.id=a.tag_value_id
        WHERE c.phone='19900006888' AND a.is_active=1 AND v.tag_value='DIASTASIS_RECTI'
        """, Integer.class)).isEqualTo(1);
  }

  private String required(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(key + " is required");
    }
    return value;
  }
}
