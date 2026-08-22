package com.privateflow.common.events;

import java.util.Map;

/** High-confidence customer facts written directly from a recognized conversation. */
public record RecognizedProfileFactsUpdatedEvent(
    Long customerId,
    Map<String, Object> fields) {
}
