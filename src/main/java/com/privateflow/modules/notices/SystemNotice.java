package com.privateflow.modules.notices;

import java.time.LocalDateTime;

public record SystemNotice(
    long id,
    String noticeId,
    String title,
    String content,
    NoticeLevel level,
    NoticeSource source,
    NoticeStatus status,
    boolean isStopped,
    LocalDateTime publishAt,
    LocalDateTime pushedAt,
    LocalDateTime expireAt,
    LocalDateTime stoppedAt,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
}
