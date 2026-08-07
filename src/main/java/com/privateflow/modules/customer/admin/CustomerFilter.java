package com.privateflow.modules.customer.admin;

import java.time.LocalDate;
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
    List<String> arrivedValues,
    LocalDateTime updatedFrom,
    LocalDateTime updatedTo,
    LocalDate appointmentFrom,
    LocalDate appointmentTo,
    LocalDateTime lastFollowupFrom,
    LocalDateTime lastFollowupTo,
    LocalDateTime nextFollowupFrom,
    LocalDateTime nextFollowupTo,
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
    arrivedValues = immutable(arrivedValues);
    tagGroups = immutable(tagGroups);
  }

  public CustomerFilter(
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
    this(keyword, sourceChannels, leadTypes, assignedKeepers, intendedStores, intendedProjects,
        customerStages, List.of(), updatedFrom, updatedTo, null, null, null, null, null, null,
        tagGroups, tagGroupLogic, sortBy, sortDirection, page, pageSize);
  }

  public static CustomerFilter empty() {
    return new CustomerFilter(
        "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        List.of(), null, null, null, null, null, null, null, null,
        List.of(), TagGroupLogic.AND, CustomerSortField.UPDATED_AT,
        SortDirection.DESC, 1, 20);
  }

  private static <T> List<T> immutable(List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
