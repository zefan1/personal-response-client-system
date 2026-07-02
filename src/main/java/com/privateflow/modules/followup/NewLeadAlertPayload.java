package com.privateflow.modules.followup;

import java.time.LocalDateTime;

public record NewLeadAlertPayload(
    String phone,
    String phoneFull,
    String nickname,
    String leadType,
    String priority,
    String sourceTable,
    String assignedKeeper,
    LocalDateTime arrivedAt
) {
}
