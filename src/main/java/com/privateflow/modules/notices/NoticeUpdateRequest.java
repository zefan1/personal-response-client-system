package com.privateflow.modules.notices;

import java.time.LocalDateTime;

public record NoticeUpdateRequest(
    String title,
    String content,
    NoticeLevel level,
    LocalDateTime publishAt,
    Integer expireDays) {
}
