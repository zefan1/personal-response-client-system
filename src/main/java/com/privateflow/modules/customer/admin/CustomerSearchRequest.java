package com.privateflow.modules.customer.admin;

import java.time.LocalDateTime;
import java.util.List;

public record CustomerSearchRequest(
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
    Integer page,
    Integer pageSize) {

  public CustomerFilter toFilter() {
    return new CustomerFilter(
        keyword,
        sourceChannels,
        leadTypes,
        assignedKeepers,
        intendedStores,
        intendedProjects,
        customerStages,
        updatedFrom,
        updatedTo,
        tagGroups,
        tagGroupLogic,
        sortBy,
        sortDirection,
        page == null ? 0 : page,
        pageSize == null ? 0 : pageSize);
  }
}
