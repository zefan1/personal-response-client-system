package com.privateflow.modules.customer.admin;

import java.time.LocalDateTime;

public record HistoricalSyncFailureResolveRequest(
    String confirmSourceTable,
    LocalDateTime before,
    String reason
) {
}
