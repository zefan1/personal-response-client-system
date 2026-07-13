package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CustomerAdminSearchRepositoryTest {

  private JdbcTemplate jdbcTemplate;
  private CustomerAdminSearchRepository repository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:admin_customer_search;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
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
    jdbcTemplate.update("""
        INSERT INTO customers (phone, nickname, source_channel, assigned_keeper, intended_store, intended_project, customer_stage, source_row_id, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, "13800001111", "王女士", "企微", "18800000001", "万江店", "产后修复", "待跟进", "wx-1111", "2026-07-13 08:00:00");
    jdbcTemplate.update("""
        INSERT INTO customers (phone, nickname, source_channel, assigned_keeper, intended_store, intended_project, customer_stage, source_row_id, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, "13900002222", "李女士", "抖音", "18800000002", "上海店", "盆底修复", "已预约", "wx-2222", "2026-07-13 09:00:00");
    repository = new CustomerAdminSearchRepository(jdbcTemplate);
  }

  @Test
  void searchesPartialPhoneAndBusinessFieldsWithoutWildcardSideEffects() {
    CustomerAdminSearchPage byPhone = repository.search("1111", 1, 20);
    CustomerAdminSearchPage byStore = repository.search("上海店", 1, 20);
    CustomerAdminSearchPage wildcard = repository.search("%", 1, 20);

    assertThat(byPhone.total()).isEqualTo(1);
    assertThat(byPhone.items()).extracting(CustomerAdminListItem::nickname).containsExactly("王女士");
    assertThat(byStore.items()).extracting(CustomerAdminListItem::phone).containsExactly("13900002222");
    assertThat(wildcard.total()).isZero();
  }

  @Test
  void supportsBlankKeywordAndStablePagination() {
    CustomerAdminSearchPage firstPage = repository.search("", 1, 1);
    CustomerAdminSearchPage secondPage = repository.search("", 2, 1);

    assertThat(firstPage.total()).isEqualTo(2);
    assertThat(firstPage.totalPages()).isEqualTo(2);
    assertThat(firstPage.items()).extracting(CustomerAdminListItem::nickname).containsExactly("李女士");
    assertThat(secondPage.items()).extracting(CustomerAdminListItem::nickname).containsExactly("王女士");
  }
}
