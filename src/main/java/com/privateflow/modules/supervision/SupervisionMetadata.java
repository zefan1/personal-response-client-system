package com.privateflow.modules.supervision;

import java.util.List;

public record SupervisionMetadata(
    List<String> operators,
    List<String> channels,
    List<String> leadSources,
    List<String> customerStages,
    List<String> eventTypes) {

  public SupervisionMetadata(
      List<String> operators,
      List<String> channels,
      List<String> leadSources,
      List<String> customerStages) {
    this(operators, channels, leadSources, customerStages, List.of());
  }

  public SupervisionMetadata {
    operators = copy(operators);
    channels = copy(channels);
    leadSources = copy(leadSources);
    customerStages = copy(customerStages);
    eventTypes = copy(eventTypes);
  }

  private static List<String> copy(List<String> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
