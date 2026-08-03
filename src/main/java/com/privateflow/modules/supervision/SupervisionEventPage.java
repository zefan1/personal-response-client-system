package com.privateflow.modules.supervision;

import java.util.List;

public record SupervisionEventPage(
    List<SupervisionEventView> items,
    long total,
    int page,
    int pageSize) {

  public SupervisionEventPage {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
