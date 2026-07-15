package com.privateflow.common.events;

import com.privateflow.modules.tags.CustomerTagUpdateResult;

public record CustomerTagsUpdatedEvent(
    String phone,
    long customerId,
    int customerVersion,
    String source,
    CustomerTagUpdateResult result
) {
}
