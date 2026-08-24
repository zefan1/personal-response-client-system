package com.privateflow.modules.customer.sync;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.tags.TagExchangeResult;
import java.util.Set;

public record FieldMappingResult(
    Customer customer,
    TagExchangeResult tagExchange,
    Set<String> mappedFields) {

  public FieldMappingResult {
    mappedFields = mappedFields == null ? Set.of() : Set.copyOf(mappedFields);
  }

  /** Backwards-compatible constructor for callers that do not need provenance. */
  public FieldMappingResult(Customer customer, TagExchangeResult tagExchange) {
    this(customer, tagExchange, Set.of());
  }
}
