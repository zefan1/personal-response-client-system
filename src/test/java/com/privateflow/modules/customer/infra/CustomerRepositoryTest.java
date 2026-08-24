package com.privateflow.modules.customer.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeSourceType;
import com.privateflow.modules.tags.TagExchangeUnmatchedValue;
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
    jdbcTemplate.execute("DROP TABLE IF EXISTS customer_field_history");
    jdbcTemplate.execute("""
        CREATE TABLE customers (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          phone VARCHAR(20),
          nickname VARCHAR(100), customer_name VARCHAR(100), wechat_id VARCHAR(100), source_channel VARCHAR(50), lead_type VARCHAR(20),
          lead_capture_type VARCHAR(100), lead_capture_method VARCHAR(100), platform_lead_at DATETIME,
          advertising_type VARCHAR(100), global_advertisement_id VARCHAR(100), standard_advertisement_id VARCHAR(100),
          content_id VARCHAR(100), video_id VARCHAR(100), order_number VARCHAR(100), conversion_trace VARCHAR(500),
          personality_type VARCHAR(50), assigned_keeper VARCHAR(50), assigned_at DATETIME,
          lead_initial_processed_at DATETIME, lead_initial_processed_by VARCHAR(50),
          lead_retained_until DATETIME, lead_invalid BOOLEAN NOT NULL DEFAULT FALSE,
          previous_assigned_keeper VARCHAR(100), previous_platform_lead_at DATETIME,
          assignment_month VARCHAR(20), intended_store VARCHAR(100),
          intended_project VARCHAR(100), purchased_project VARCHAR(200), postpartum_months DECIMAL(4,1),
          experience_card_type VARCHAR(50), pending_order_status VARCHAR(100), purchase_date DATE,
          customer_level VARCHAR(20),
          parity VARCHAR(10), delivery_method VARCHAR(20), breastfeeding VARCHAR(20), lochia_period VARCHAR(50),
          pregnancy_weight DECIMAL(5,1), current_weight DECIMAL(5,1), body_concerns VARCHAR(500),
          diastasis_recti VARCHAR(50), urine_leakage VARCHAR(100), pubic_lumbago VARCHAR(100),
          prev_repair_exp VARCHAR(500), postpartum_check VARCHAR(200), exercise_habits VARCHAR(200),
          intent_level VARCHAR(10), worries VARCHAR(500), customer_stage VARCHAR(50),
          internal_note TEXT, customer_profile_summary TEXT,
          first_tracking_capture TEXT, second_tracking_capture TEXT, third_tracking_capture TEXT,
          last_followup_at DATETIME, followup_notes TEXT, next_followup_at DATETIME, next_followup_dir VARCHAR(200),
          appointment_date DATE, appointment_store VARCHAR(100), appointment_item VARCHAR(100), arrived VARCHAR(10),
          appointment_status VARCHAR(20), appointment_time VARCHAR(10), arrival_source_row_id VARCHAR(100),
          arrival_handover_record TEXT, arrival_project_type VARCHAR(100), arrival_experience_project VARCHAR(500),
          historical_experience_count VARCHAR(100), customer_report TEXT, reception_teacher VARCHAR(100),
          reception_consultant VARCHAR(100), voucher_redeemed VARCHAR(20), transaction_amount DECIMAL(12,2),
          transaction_at DATETIME, transaction_primary_reason VARCHAR(500),
          source_table VARCHAR(100), source_row_id VARCHAR(100), synced_at DATETIME, version INT NOT NULL DEFAULT 0,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          UNIQUE (phone)
        )
         """);
    jdbcTemplate.execute("""
        CREATE TABLE customer_field_history (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          customer_id BIGINT NOT NULL,
          field_name VARCHAR(100) NOT NULL,
          field_value VARCHAR(500),
          source VARCHAR(200) NOT NULL,
          source_field VARCHAR(200) NOT NULL,
          operator VARCHAR(100) NOT NULL,
          changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
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
  void createsRecognitionCustomerWithoutPhoneAndReturnsItsDatabaseId() {
    Customer first = new Customer();
    first.setNickname("截图客户");
    first.setLeadType("PENDING");

    Customer second = new Customer();
    second.setNickname("截图客户");
    second.setLeadType("PENDING");

    Customer firstSaved = repository.createRecognitionCustomer(first);
    Customer secondSaved = repository.createRecognitionCustomer(second);

    assertThat(firstSaved.getId()).isNotNull();
    assertThat(secondSaved.getId()).isNotNull().isNotEqualTo(firstSaved.getId());
    assertThat(firstSaved.getPhone()).isNull();
    assertThat(repository.findById(firstSaved.getId())).isPresent();
  }

  @Test
  void keywordSearchMatchesNicknameButNotFollowupNotes() {
    jdbcTemplate.update("""
        INSERT INTO customers (phone, nickname, followup_notes, version)
        VALUES ('18810001001', '林晓雯', '客户希望确认价格和周末时间。', 0)
        """);
    jdbcTemplate.update("""
        INSERT INTO customers (phone, nickname, followup_notes, version)
        VALUES ('18810001003', '周雅婷', '等待到店检测。', 0)
        """);

    List<Customer> customers = repository.searchByKeyword("周", 10);

    assertThat(customers).extracting(Customer::getNickname).containsExactly("周雅婷");
  }

  @Test
  void keywordSearchIncludesRecognitionCustomerWithoutPhone() {
    jdbcTemplate.update("""
        INSERT INTO customers (phone, nickname, version)
        VALUES (NULL, '少花', 0)
        """);

    assertThat(repository.searchByKeyword("少花", 10))
        .extracting(Customer::getNickname)
        .containsExactly("少花");
  }

  @Test
  void keywordSearchMatchesFullPhoneAndPhoneSuffix() {
    jdbcTemplate.update("""
        INSERT INTO customers (phone, nickname, version)
        VALUES ('18810001003', '周雅婷', 0)
        """);

    assertThat(repository.searchByKeyword("18810001003", 10))
        .extracting(Customer::getNickname)
        .containsExactly("周雅婷");
    assertThat(repository.searchByKeyword("1003", 10))
        .extracting(Customer::getNickname)
        .containsExactly("周雅婷");
  }

  @Test
  void sourceAwareUpsertPassesNormalizedExchangeResultToTagBridge() {
    Customer customer = new Customer();
    customer.setPhone("13800000000");
    customer.setIntentLevel("HIGH");

    TagExchangeResult exchange = new TagExchangeResult(
        Map.of("intentLevel", "HIGH"),
        List.of(),
        List.of(new TagExchangeUnmatchedValue(
            "intentLevel",
            "UNKNOWN",
            List.of("UNKNOWN"),
            2L,
            TagExchangeSourceType.EXTERNAL_SYNC,
            "22")));

    assertThat(repository.upsert(customer, exchange, TagExchangeSourceType.EXTERNAL_SYNC, "22"))
        .isTrue();
    verify(synchronizer).synchronize(
        "13800000000",
        exchange,
        TagExchangeSourceType.EXTERNAL_SYNC,
        "22");
  }

  @Test
  void upsertAndReadKeepInternalProfileAndTrackingFields() {
    Customer customer = new Customer();
    customer.setPhone("13800000001");
    customer.setInternalNote("客户重视安全感，先说明评估流程");
    customer.setCustomerProfileSummary("产后6个月，顺产，关注腹直肌和腰痛");
    customer.setFirstTrackingCapture("首次关注恢复周期");
    customer.setSecondTrackingCapture("明确周一上午可联系");
    customer.setThirdTrackingCapture("希望先了解门店评估流程");

    assertThat(repository.upsert(customer)).isTrue();

    Customer saved = repository.findByPhone("13800000001").orElseThrow();
    assertThat(saved.getInternalNote()).isEqualTo("客户重视安全感，先说明评估流程");
    assertThat(saved.getCustomerProfileSummary()).isEqualTo("产后6个月，顺产，关注腹直肌和腰痛");
    assertThat(saved.getFirstTrackingCapture()).isEqualTo("首次关注恢复周期");
    assertThat(saved.getSecondTrackingCapture()).isEqualTo("明确周一上午可联系");
    assertThat(saved.getThirdTrackingCapture()).isEqualTo("希望先了解门店评估流程");
  }

  @Test
  void upsertAndReadKeepLeadCaptureAndAssignmentFacts() {
    Customer customer = new Customer();
    customer.setPhone("13800000002");
    customer.setWechatId("wxid_test");
    customer.setLeadCaptureType("表单留资");
    customer.setLeadCaptureMethod("落地页");
    customer.setPlatformLeadAt(java.time.LocalDateTime.of(2026, 8, 14, 10, 30));
    customer.setAssignedAt(java.time.LocalDateTime.of(2026, 8, 14, 11, 0));

    assertThat(repository.upsert(customer)).isTrue();

    Customer saved = repository.findByPhone("13800000002").orElseThrow();
    assertThat(saved.getWechatId()).isEqualTo("wxid_test");
    assertThat(saved.getLeadCaptureType()).isEqualTo("表单留资");
    assertThat(saved.getLeadCaptureMethod()).isEqualTo("落地页");
    assertThat(saved.getPlatformLeadAt()).isEqualTo(java.time.LocalDateTime.of(2026, 8, 14, 10, 30));
    assertThat(saved.getAssignedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 8, 14, 11, 0));
  }

  @Test
  void upsertAndReadKeepLeadProcessingValidityFields() {
    Customer customer = new Customer();
    customer.setPhone("13800000003");
    customer.setLeadInvalid(true);
    customer.setLeadInitialProcessedAt(java.time.LocalDateTime.of(2026, 8, 23, 18, 0));
    customer.setLeadInitialProcessedBy("企业微信同步");
    customer.setLeadRetainedUntil(java.time.LocalDateTime.of(2026, 8, 24, 18, 0));

    assertThat(repository.upsert(customer)).isTrue();
    assertThat(repository.updateLeadProcessingState(customer)).isEqualTo(1);

    Customer saved = repository.findByPhone("13800000003").orElseThrow();
    assertThat(saved.isLeadInvalid()).isTrue();
    assertThat(saved.getLeadInitialProcessedAt())
        .isEqualTo(java.time.LocalDateTime.of(2026, 8, 23, 18, 0));
    assertThat(saved.getLeadInitialProcessedBy()).isEqualTo("企业微信同步");
    assertThat(saved.getLeadRetainedUntil())
        .isEqualTo(java.time.LocalDateTime.of(2026, 8, 24, 18, 0));
  }

  @Test
  void findsLatestStageBeforeInvalidMark() {
    jdbcTemplate.update("INSERT INTO customers (phone, customer_stage) VALUES (?, ?)",
        "13800000004", "无效线索");
    long customerId = jdbcTemplate.queryForObject(
        "SELECT id FROM customers WHERE phone = ?", Long.class, "13800000004");
    jdbcTemplate.update("""
        INSERT INTO customer_field_history
          (customer_id, field_name, field_value, source, source_field, operator)
        VALUES (?, 'customerStage', '破冰阶段', 'external', '客户阶段', 'SYSTEM'),
               (?, 'customerStage', '无效线索', 'external', '客户阶段', 'SYSTEM')
        """, customerId, customerId);

    assertThat(repository.findPreviousCustomerStage("13800000004"))
        .contains("破冰阶段");
  }
}
