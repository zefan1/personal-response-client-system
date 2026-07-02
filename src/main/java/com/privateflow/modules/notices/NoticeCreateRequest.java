package com.privateflow.modules.notices;

import java.time.LocalDateTime;

public record NoticeCreateRequest(
    String title,
    String content,
    NoticeLevel level,
    PublishType publishType,
    LocalDateTime publishAt,
    Integer expireDays) {
}
