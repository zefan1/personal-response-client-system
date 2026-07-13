package com.privateflow.modules.match;

import java.time.LocalDateTime;

public record CustomerSummary(
    String phone,
    String phoneFull,
    String nickname,
    String leadType,
    String assignedKeeper,
    LocalDateTime lastFollowupAt,
    String intendedStore,
    Confidence confidence
) {
}
