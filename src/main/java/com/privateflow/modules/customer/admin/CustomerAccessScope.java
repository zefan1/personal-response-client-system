package com.privateflow.modules.customer.admin;

import java.util.List;

public record CustomerAccessScope(
    boolean unrestricted,
    List<String> permittedKeepers,
    boolean excludeUnassigned) {

  public CustomerAccessScope {
    permittedKeepers = permittedKeepers == null ? List.of() : List.copyOf(permittedKeepers);
  }

  public static CustomerAccessScope all() {
    return new CustomerAccessScope(true, List.of(), false);
  }
}
