package com.privateflow.modules.customer.sync;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.tags.TagExchangeResult;

public record FieldMappingResult(
    Customer customer,
    TagExchangeResult tagExchange) {
}
