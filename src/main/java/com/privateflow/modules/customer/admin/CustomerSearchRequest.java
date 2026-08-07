package com.privateflow.modules.customer.admin;

import java.time.LocalDate;
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
    Integer page,
    Integer pageSize) {

  public CustomerSearchRequest(
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
    this(keyword, sourceChannels, leadTypes, assignedKeepers, intendedStores, intendedProjects,
        customerStages, List.of(), updatedFrom, updatedTo, null, null, null, null, null, null,
        tagGroups, tagGroupLogic, sortBy, sortDirection, page, pageSize);
  }

  public CustomerFilter toFilter() {
    return new CustomerFilter(
        keyword,
        sourceChannels,
        leadTypes,
        assignedKeepers,
        intendedStores,
        intendedProjects,
        customerStages,
        arrivedValues,
        updatedFrom,
        updatedTo,
        appointmentFrom,
        appointmentTo,
        lastFollowupFrom,
        lastFollowupTo,
        nextFollowupFrom,
        nextFollowupTo,
        tagGroups,
        tagGroupLogic,
        sortBy,
        sortDirection,
        page == null ? 0 : page,
        pageSize == null ? 0 : pageSize);
  }
}
