package com.privateflow.common.events;

import java.time.Instant;

public record ImageServiceStatusEvent(
    String status,
    int failedCount,
    String lastErrorMsg,
    Instant timestamp
) {
}
