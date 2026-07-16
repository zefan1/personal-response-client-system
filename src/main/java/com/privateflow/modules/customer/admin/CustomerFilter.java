package com.privateflow.modules.customer.admin;

import java.time.LocalDateTime;
import java.util.List;

public record CustomerFilter(
    String keyword,
    List<String> sourceChannels,
    List<String> leadTypes,
    List<String> assignedKeepers,
    List<String> intendedStores,
    List<String> intendedProjects,
    List<String> customerStages,
    LocalDateTime updatedFrom,
    LocalDateTime updatedTo,
    List<TagFilterGroup> tagGroups,
    TagGroupLogic tagGroupLogic,
    CustomerSortField sortBy,
    SortDirection sortDirection,
    int page,
    int pageSize) {

  public CustomerFilter {
    sourceChannels = immutable(sourceChannels);
    leadTypes = immutable(leadTypes);
    assignedKeepers = immutable(assignedKeepers);
    intendedStores = immutable(intendedStores);
    intendedProjects = immutable(intendedProjects);
    customerStages = immutable(customerStages);
    tagGroups = immutable(tagGroups);
  }

  public static CustomerFilter empty() {
    return new CustomerFilter(
        "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        null, null, List.of(), TagGroupLogic.AND, CustomerSortField.UPDATED_AT,
        SortDirection.DESC, 1, 20);
  }

  private static <T> List<T> immutable(List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
