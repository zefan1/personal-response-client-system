package com.privateflow.modules.followup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.privateflow.modules.tags.CustomerTagFoundationRepository;
import com.privateflow.modules.tags.CustomerTagQueryDto;
import com.privateflow.modules.tags.TagAutoUpdateMode;
import com.privateflow.modules.tags.TagCategory;
import com.privateflow.modules.tags.TagDirectoryService;
import com.privateflow.modules.tags.TagDirectorySnapshot;
import com.privateflow.modules.tags.TagImpact;
import com.privateflow.modules.tags.TagSelectionMode;
import com.privateflow.modules.tags.TagUncertainPolicy;
import com.privateflow.modules.tags.TagValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.privateflow.modules.customer.Customer;

class FollowupTagContextLoaderTest {

  @Test
  void filtersDisabledMergedAndWrongPurposeAssignments() {
    CustomerTagFoundationRepository repository = Mockito.mock(CustomerTagFoundationRepository.class);
    TagDirectoryService directory = Mockito.mock(TagDirectoryService.class);
    FollowupTagContextLoader loader = new FollowupTagContextLoader(repository, directory);
    when(repository.findCurrentTagDetails(7L)).thenReturn(List.of(
        detail(1L, 50L, 51L, true, null, true, null),
        detail(2L, 50L, 52L, false, null, true, null),
        detail(3L, 50L, 53L, true, null, true, 60L)));
    when(directory.getSnapshot()).thenReturn(TagDirectorySnapshot.from(
        List.of(category(50L, true, null, value(51L, 50L, true, null))), Instant.now()));

    assertThat(loader.load(customer(7L)).valueIdsByCategory()).containsEntry(50L, Set.of(51L));
  }

  @Test
  void categoryDisabledOrFollowupPurposeClosedDoesNotMatch() {
    CustomerTagFoundationRepository repository = Mockito.mock(CustomerTagFoundationRepository.class);
    TagDirectoryService directory = Mockito.mock(TagDirectoryService.class);
    FollowupTagContextLoader loader = new FollowupTagContextLoader(repository, directory);
    when(repository.findCurrentTagDetails(7L)).thenReturn(List.of(detail(1L, 50L, 51L, true, null, true, null)));
    when(directory.getSnapshot()).thenReturn(TagDirectorySnapshot.from(
        List.of(category(50L, false, null, value(51L, 50L, true, null))), Instant.now()));

    assertThat(loader.load(customer(7L)).valueIdsByCategory()).isEmpty();
  }

  private Customer customer(long id) {
    Customer customer = new Customer();
    customer.setId(id);
    return customer;
  }

  private CustomerTagQueryDto detail(long assignmentId, long categoryId, long valueId,
      boolean active, Long categoryMerged, boolean valueEnabled, Long valueMerged) {
    return new CustomerTagQueryDto(
        assignmentId, 7L, 1, categoryId, "intent_level", "意向度", TagSelectionMode.SINGLE,
        true, categoryMerged, 1, valueId, "HIGH", "高意向", valueEnabled, valueMerged, 1,
        TagSelectionMode.SINGLE, active, "MANUAL", BigDecimal.ONE, "manual", 1, null,
        null, null, null, null, "keeper", false, null, null, null, null, null,
        LocalDateTime.now(), LocalDateTime.now());
  }

  private TagCategory category(long id, boolean followup, Long merged, TagValue value) {
    LocalDateTime now = LocalDateTime.now();
    return new TagCategory(id, "intent_level", "意向度", "用途", null, TagSelectionMode.SINGLE,
        true, true, TagAutoUpdateMode.RECORD_ONLY, new BigDecimal("0.85"), 1, 0,
        TagUncertainPolicy.KEEP_CURRENT, true, true, true, followup, false, true, 1,
        merged, 1, List.of(value), TagImpact.empty(), now, now);
  }

  private TagValue value(long id, long categoryId, boolean enabled, Long merged) {
    LocalDateTime now = LocalDateTime.now();
    return new TagValue(id, categoryId, "intent_level", "HIGH", "高意向", "含义", "", "", "", "",
        List.of(), true, true, enabled, 1, merged, 1, TagImpact.empty(), now, now);
  }
}
