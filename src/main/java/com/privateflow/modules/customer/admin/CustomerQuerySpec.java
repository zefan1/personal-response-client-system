package com.privateflow.modules.customer.admin;

import java.util.List;

public record CustomerQuerySpec(
    String whereClause,
    List<Object> args,
    String orderClause) {

  public CustomerQuerySpec {
    args = args == null ? List.of() : List.copyOf(args);
  }
}
