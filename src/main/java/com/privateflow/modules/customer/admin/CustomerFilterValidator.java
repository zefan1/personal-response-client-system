package com.privateflow.modules.customer.admin;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.tags.TagCandidateBuilder;
import com.privateflow.modules.tags.TagCandidatePurpose;
import com.privateflow.modules.tags.TagCategory;
import com.privateflow.modules.tags.TagSelectionMode;
import com.privateflow.modules.tags.TagValue;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CustomerFilterValidator {

  private final TagCandidateBuilder tagCandidateBuilder;

  public CustomerFilterValidator(TagCandidateBuilder tagCandidateBuilder) {
    this.tagCandidateBuilder = tagCandidateBuilder;
  }

  public CustomerFilter validate(CustomerFilter filter) {
    if (filter == null) {
      return CustomerFilter.empty();
    }
    String keyword = normalize(filter.keyword());
    if (keyword.length() > 100) {
      throw badRequest("keyword must be at most 100 characters");
    }
    if (filter.updatedFrom() != null
        && filter.updatedTo() != null
        && filter.updatedFrom().isAfter(filter.updatedTo())) {
      throw badRequest("updatedFrom must not be after updatedTo");
    }
    if (filter.pageSize() > 50) {
      throw badRequest("pageSize must be between 1 and 50");
    }
    List<TagFilterGroup> tagGroups = normalizeTagGroups(filter.tagGroups());
    return new CustomerFilter(
        keyword,
        normalizeList(filter.sourceChannels()),
        normalizeList(filter.leadTypes()),
        normalizeList(filter.assignedKeepers()),
        normalizeList(filter.intendedStores()),
        normalizeList(filter.intendedProjects()),
        normalizeList(filter.customerStages()),
        filter.updatedFrom(),
        filter.updatedTo(),
        tagGroups,
        filter.tagGroupLogic() == null ? TagGroupLogic.AND : filter.tagGroupLogic(),
        filter.sortBy() == null ? CustomerSortField.UPDATED_AT : filter.sortBy(),
        filter.sortDirection() == null ? SortDirection.DESC : filter.sortDirection(),
        filter.page() < 1 ? 1 : filter.page(),
        filter.pageSize() < 1 ? 20 : filter.pageSize());
  }

  private ApiException badRequest(String message) {
    return new ApiException(ApiErrorCodes.BAD_REQUEST, message);
  }

  private List<TagFilterGroup> normalizeTagGroups(List<TagFilterGroup> groups) {
    if (groups == null || groups.isEmpty()) {
      return List.of();
    }
    Map<Long, TagCategory> categories = tagCandidateBuilder.build(TagCandidatePurpose.FILTER).stream()
        .collect(Collectors.toMap(TagCategory::id, Function.identity(), (left, right) -> left));
    LinkedHashSet<Long> seenCategories = new LinkedHashSet<>();
    List<TagFilterGroup> normalized = new ArrayList<>();
    for (TagFilterGroup group : groups) {
      if (group == null || group.valueIds() == null || group.valueIds().isEmpty()) {
        throw badRequest("tag filter group must contain at least one value");
      }
      TagCategory category = categories.get(group.categoryId());
      if (category == null) {
        throw badRequest("tag filter category is not available");
      }
      if (!seenCategories.add(group.categoryId())) {
        throw badRequest("tag filter category must not be repeated");
      }
      LinkedHashSet<Long> valueIds = new LinkedHashSet<>(group.valueIds());
      if (valueIds.size() != group.valueIds().size()) {
        throw badRequest("tag filter values must not be repeated");
      }
      Map<Long, TagValue> values = category.values().stream()
          .collect(Collectors.toMap(TagValue::id, Function.identity(), (left, right) -> left));
      if (valueIds.stream().anyMatch(valueId -> !values.containsKey(valueId))) {
        throw badRequest("tag filter value does not belong to category");
      }
      TagMatchMode match = group.match() == null ? TagMatchMode.ANY : group.match();
      if (category.selectionMode() == TagSelectionMode.SINGLE
          && (valueIds.size() != 1 || match == TagMatchMode.ALL)) {
        throw badRequest("single-selection category accepts one value with ANY match only");
      }
      normalized.add(new TagFilterGroup(category.id(), List.copyOf(valueIds), match));
    }
    return List.copyOf(normalized);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private List<String> normalizeList(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String value : values) {
      String trimmed = normalize(value);
      if (!trimmed.isBlank()) {
        normalized.add(trimmed);
      }
    }
    return List.copyOf(new ArrayList<>(normalized));
  }
}
