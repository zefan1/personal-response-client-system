package com.privateflow.modules.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
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
