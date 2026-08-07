package com.privateflow.modules.customer.admin;

import java.util.List;

public record CustomerFilterOptions(
    List<String> sourceChannels,
    List<String> leadTypes,
    List<String> assignedKeepers,
    List<String> intendedStores,
    List<String> intendedProjects,
    List<String> customerStages,
    List<String> arrivedValues) {

  public CustomerFilterOptions {
    sourceChannels = immutable(sourceChannels);
    leadTypes = immutable(leadTypes);
    assignedKeepers = immutable(assignedKeepers);
    intendedStores = immutable(intendedStores);
    intendedProjects = immutable(intendedProjects);
    customerStages = immutable(customerStages);
    arrivedValues = immutable(arrivedValues);
  }

  private static List<String> immutable(List<String> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
