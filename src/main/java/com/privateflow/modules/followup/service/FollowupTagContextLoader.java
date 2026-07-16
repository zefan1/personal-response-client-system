package com.privateflow.modules.followup.service;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.tags.CustomerTagFoundationRepository;
import com.privateflow.modules.tags.CustomerTagQueryDto;
import com.privateflow.modules.tags.TagCategory;
import com.privateflow.modules.tags.TagDirectoryService;
import com.privateflow.modules.tags.TagDirectorySnapshot;
import com.privateflow.modules.tags.TagValue;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FollowupTagContextLoader {

  private final CustomerTagFoundationRepository tagRepository;
  private final TagDirectoryService directoryService;

  public FollowupTagContextLoader(
      CustomerTagFoundationRepository tagRepository,
      TagDirectoryService directoryService) {
    this.tagRepository = tagRepository;
    this.directoryService = directoryService;
  }

  public FollowupTagContext load(Customer customer) {
    if (customer == null || customer.getId() == null || customer.getId() <= 0) {
      return FollowupTagContext.empty();
    }
    TagDirectorySnapshot snapshot = directoryService.getSnapshot();
    List<CustomerTagQueryDto> assignments = tagRepository.findCurrentTagDetails(customer.getId());
    Map<Long, Set<Long>> valuesByCategory = new LinkedHashMap<>();
    for (CustomerTagQueryDto assignment : assignments) {
      if (!isEffective(assignment, snapshot)) {
        continue;
      }
      valuesByCategory.computeIfAbsent(assignment.categoryId(), ignored -> new LinkedHashSet<>())
          .add(assignment.tagValueId());
    }
    return FollowupTagContext.of(valuesByCategory);
  }

  private boolean isEffective(CustomerTagQueryDto assignment, TagDirectorySnapshot snapshot) {
    if (assignment == null || !assignment.active() || !assignment.categoryEnabled()
        || assignment.categoryMergedIntoId() != null || !assignment.tagValueEnabled()
        || assignment.tagValueMergedIntoId() != null) {
      return false;
    }
    TagCategory category = snapshot.categoriesById().get(assignment.categoryId());
    TagValue value = snapshot.valuesById().get(assignment.tagValueId());
    return category != null && category.isEnabled() && category.mergedIntoId() == null
        && category.useForFollowupRules() && value != null && value.isEnabled()
        && value.mergedIntoId() == null && value.categoryId() == category.id();
  }
}
