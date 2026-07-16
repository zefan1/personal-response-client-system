package com.privateflow.modules.analytics;

import java.time.LocalDateTime;

public record TagAnalyticsWindow(
    LocalDateTime from,
    LocalDateTime to,
    TagTrendGranularity granularity) {
}
