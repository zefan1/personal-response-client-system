package com.privateflow.modules.customer.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeSourceType;
import com.privateflow.modules.tags.LegacyCustomerTagSynchronizer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CustomerRepositoryTest {

  private JdbcTemplate jdbcTemplate;
  private CustomerRepository repository;
  private LegacyCustomerTagSynchronizer synchronizer;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:customer_repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS customers");
    jdbcTemplate.execute("""
        CREATE TABLE customers (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          phone VARCHAR(20) NOT NULL,
          nickname VARCHAR(100), source_channel VARCHAR(50), lead_type VARCHAR(20),
          personality_type VARCHAR(50), assigned_keeper VARCHAR(50), intended_store VARCHAR(100),
          intended_project VARCHAR(100), purchased_project VARCHAR(200), postpartum_months DECIMAL(4,1),
          parity VARCHAR(10), delivery_method VARCHAR(20), breastfeeding VARCHAR(20), lochia_period VARCHAR(50),
          pregnancy_weight DECIMAL(5,1), current_weight DECIMAL(5,1), body_concerns VARCHAR(500),
          diastasis_recti VARCHAR(50), urine_leakage VARCHAR(100), pubic_lumbago VARCHAR(100),
          prev_repair_exp VARCHAR(500), postpartum_check VARCHAR(200), exercise_habits VARCHAR(200),
          intent_level VARCHAR(10), worries VARCHAR(500), customer_stage VARCHAR(50),
          last_followup_at DATETIME, followup_notes TEXT, next_followup_at DATETIME, next_followup_dir VARCHAR(200),
          appointment_date DATE, appointment_store VARCHAR(100), appointment_item VARCHAR(100), arrived VARCHAR(10),
          source_table VARCHAR(100), source_row_id VARCHAR(100), synced_at DATETIME, version INT NOT NULL DEFAULT 0,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          UNIQUE (phone)
        )
        """);
    synchronizer = mock(LegacyCustomerTagSynchronizer.class);
    repository = new CustomerRepository(jdbcTemplate, synchronizer);
  }

  @Test
  void findsCustomerByDatabaseIdAndReturnsEmptyWhenMissing() {
    jdbcTemplate.update("""
        INSERT INTO customers (id, phone, nickname, assigned_keeper, version)
        VALUES (7, '13800000000', '王女士', 'real-keeper', 3)
        """);

    Customer customer = repository.findById(7L).orElseThrow();

    assertThat(customer.getId()).isEqualTo(7L);
    assertThat(customer.getPhone()).isEqualTo("13800000000");
    assertThat(customer.getAssignedKeeper()).isEqualTo("real-keeper");
    assertThat(customer.getVersion()).isEqualTo(3);
    assertThat(repository.findById(8L)).isEmpty();
  }

  @Test
  void sourceAwareUpsertPassesNormalizedExchangeResultToTagBridge() {
    Customer customer = new Customer();
    customer.setPhone("13800000000");
    customer.setIntentLevel("HIGH");

    TagExchangeResult exchange = new TagExchangeResult(
        Map.of("intentLevel", "HIGH"),
        List.of(),
        List.of());

    assertThat(repository.upsert(customer, exchange, TagExchangeSourceType.EXTERNAL_SYNC, "22"))
        .isTrue();
    verify(synchronizer).synchronize(
        "13800000000",
        Map.of("intentLevel", "HIGH"),
        TagExchangeSourceType.EXTERNAL_SYNC,
        "22");
  }
}
