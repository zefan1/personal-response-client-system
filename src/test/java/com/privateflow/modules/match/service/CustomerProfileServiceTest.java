package com.privateflow.modules.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.communication.CommunicationArchiveRepository;
import com.privateflow.modules.profile.ProfileSuggestion;
import com.privateflow.modules.profile.service.SuggestionQueueManager;
import com.privateflow.modules.tags.CustomerTagCategoryLock;
import com.privateflow.modules.tags.CustomerTagFoundationRepository;
import com.privateflow.modules.tags.CustomerTagQueryDto;
import com.privateflow.modules.tags.TagCandidateBuilder;
import com.privateflow.modules.tags.TagCandidatePurpose;
import com.privateflow.modules.tags.TagCategory;
import com.privateflow.modules.tags.TagImpact;
import com.privateflow.modules.tags.TagSelectionMode;
import com.privateflow.modules.tags.TagUncertainPolicy;
import com.privateflow.modules.tags.TagValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CustomerProfileServiceTest {

  @Test
  void profileIncludesCurrentTagsLocksAndManualDirectoryCandidates() {
    CustomerQueryService customerQueryService = mock(CustomerQueryService.class);
    SuggestionQueueManager suggestionQueueManager = mock(SuggestionQueueManager.class);
    CustomerAccessService accessService = mock(CustomerAccessService.class);
    CustomerTagFoundationRepository tagRepository = mock(CustomerTagFoundationRepository.class);
    TagCandidateBuilder candidateBuilder = mock(TagCandidateBuilder.class);
    Customer customer = customer(7L);
    TagCategory category = category();
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    when(accessService.canAccess(customer)).thenReturn(true);
    when(suggestionQueueManager.listPending("18800001111")).thenReturn(List.<ProfileSuggestion>of());
    when(tagRepository.findCurrentTagDetails(7L)).thenReturn(List.of(tagDetail()));
    when(tagRepository.findCategoryLocks(7L)).thenReturn(List.of(lock()));
    when(candidateBuilder.build(TagCandidatePurpose.MANUAL_ASSIGNMENT)).thenReturn(List.of(category));
    CustomerProfileService service = new CustomerProfileService(
        customerQueryService,
        suggestionQueueManager,
        accessService,
        tagRepository,
        candidateBuilder);

    var view = service.getProfile("18800001111");

    assertThat(view.currentTags()).containsExactly(tagDetail());
    assertThat(view.tagLocks()).containsExactly(lock());
    assertThat(view.editableTagCategories()).containsExactly(category);
  }

  @Test
  void profilePreservesExistingAnalysisAndCommunicationSummaryFields() {
    CustomerQueryService customerQueryService = mock(CustomerQueryService.class);
    SuggestionQueueManager suggestionQueueManager = mock(SuggestionQueueManager.class);
    CustomerAccessService accessService = mock(CustomerAccessService.class);
    Customer customer = customer(7L);
    customer.setInternalNote("重点关注客户的恢复节奏");
    customer.setCustomerProfileSummary("宝宝3个月，关注腹部恢复和持续腰痛");
    customer.setFirstTrackingCapture("首次提到腰痛");
    customer.setSecondTrackingCapture("开始了解恢复方案");
    customer.setThirdTrackingCapture("准备预约基础评估");
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    when(accessService.canAccess(customer)).thenReturn(true);
    when(suggestionQueueManager.listPending("18800001111")).thenReturn(List.of());
    CustomerProfileService service = new CustomerProfileService(
        customerQueryService,
        suggestionQueueManager,
        accessService);

    Customer profile = service.getProfile("18800001111").customer();

    assertThat(profile.getInternalNote()).isEqualTo("重点关注客户的恢复节奏");
    assertThat(profile.getCustomerProfileSummary())
        .isEqualTo("宝宝3个月，关注腹部恢复和持续腰痛");
    assertThat(profile.getFirstTrackingCapture()).isEqualTo("首次提到腰痛");
    assertThat(profile.getSecondTrackingCapture()).isEqualTo("开始了解恢复方案");
    assertThat(profile.getThirdTrackingCapture()).isEqualTo("准备预约基础评估");
  }

  @Test
  void profileIncludesTheLatestCommunicationSummaryVersion() throws Exception {
    CustomerQueryService customerQueryService = mock(CustomerQueryService.class);
    SuggestionQueueManager suggestionQueueManager = mock(SuggestionQueueManager.class);
    CustomerAccessService accessService = mock(CustomerAccessService.class);
    CustomerTagFoundationRepository tagRepository = mock(CustomerTagFoundationRepository.class);
    TagCandidateBuilder candidateBuilder = mock(TagCandidateBuilder.class);
    Customer customer = customer(7L);
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    when(accessService.canAccess(customer)).thenReturn(true);
    when(suggestionQueueManager.listPending("18800001111")).thenReturn(List.of());

    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:profile-summary;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS communication_summary_versions");
    jdbcTemplate.execute("""
        CREATE TABLE communication_summary_versions (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          customer_id BIGINT NOT NULL,
          version_no INT NOT NULL,
          summary_text VARCHAR(500) NOT NULL,
          last_message_id BIGINT NOT NULL,
          generated_at TIMESTAMP NOT NULL,
          created_at TIMESTAMP NOT NULL
        )
        """);
    jdbcTemplate.update("""
        INSERT INTO communication_summary_versions (
          customer_id, version_no, summary_text, last_message_id, generated_at, created_at
        ) VALUES (7, 1, '旧汇总', 9, '2026-07-31 18:00:00', '2026-07-31 18:00:00'),
                 (7, 2, '最新汇总', 11, '2026-08-01 10:20:00', '2026-08-01 10:20:00')
        """);
    CommunicationArchiveRepository communicationRepository =
        new CommunicationArchiveRepository(jdbcTemplate);

    CustomerProfileService service = new CustomerProfileService(
        customerQueryService,
        suggestionQueueManager,
        accessService,
        tagRepository,
        candidateBuilder,
        communicationRepository);

    var latest = service.getProfile("18800001111").latestCommunicationSummary();
    assertThat(latest.summaryText()).isEqualTo("最新汇总");
    assertThat(latest.generatedAt()).isEqualTo(LocalDateTime.parse("2026-08-01T10:20:00"));
  }

  @Test
  void profileCanBeOpenedByCustomerIdWhenTheLocalRecognitionCustomerHasNoPhone() {
    CustomerQueryService customerQueryService = mock(CustomerQueryService.class);
    SuggestionQueueManager suggestionQueueManager = mock(SuggestionQueueManager.class);
    CustomerAccessService accessService = mock(CustomerAccessService.class);
    Customer customer = customer(44L);
    customer.setPhone(null);
    customer.setNickname("小雨");
    when(customerQueryService.getById(44L)).thenReturn(customer);
    when(accessService.canAccess(customer)).thenReturn(true);
    CustomerProfileService service = new CustomerProfileService(
        customerQueryService,
        suggestionQueueManager,
        accessService);

    Customer profile = service.getProfileById(44L).customer();

    assertThat(profile.getId()).isEqualTo(44L);
    assertThat(profile.getPhone()).isNull();
    assertThat(profile.getNickname()).isEqualTo("小雨");
  }

  private Customer customer(long id) {
    Customer customer = new Customer();
    customer.setId(id);
    customer.setPhone("18800001111");
    customer.setVersion(3);
    return customer;
  }

  private TagCategory category() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
    TagValue value = new TagValue(
        12L, 1L, "intent_level", "HIGH", "高意向", "", "", "", "", "", List.of(),
        true, true, true, 1, null, 0, TagImpact.empty(), now, now);
    return new TagCategory(
        1L, "intent_level", "意向度", "识别客户购买意向", "intentLevel", TagSelectionMode.SINGLE,
        true, true, com.privateflow.modules.tags.TagAutoUpdateMode.REPLACE,
        new BigDecimal("0.8500"), 3, 24, TagUncertainPolicy.KEEP_CURRENT,
        true, true, true, true, true, true, 1, null, 0, List.of(value), TagImpact.empty(), now, now);
  }

  private CustomerTagQueryDto tagDetail() {
    return new CustomerTagQueryDto(
        21L, 7L, 3, 1L, "intent_level", "意向度", TagSelectionMode.SINGLE, true, null, 0,
        12L, "HIGH", "高意向", true, null, 0, TagSelectionMode.SINGLE, true, "MANUAL",
        null, "客户明确确认购买", 0, null, null, null, null, null, "keeper-auth", true,
        "keeper-auth", LocalDateTime.of(2026, 7, 15, 10, 0), null, null, null,
        LocalDateTime.of(2026, 7, 15, 10, 0), LocalDateTime.of(2026, 7, 15, 10, 0));
  }

  private CustomerTagCategoryLock lock() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
    return new CustomerTagCategoryLock(
        31L, 7L, 1L, true, "keeper-auth", "人工修改", now, null, null, 0, now, now);
  }
}
