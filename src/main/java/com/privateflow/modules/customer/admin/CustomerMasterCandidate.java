package com.privateflow.modules.customer.admin;

import java.time.LocalDateTime;

public record CustomerMasterCandidate(
    long id,
    String nickname,
    String phone,
    String wechatId,
    String sourceTable,
    LocalDateTime updatedAt) {
}
