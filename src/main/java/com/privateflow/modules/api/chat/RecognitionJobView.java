package com.privateflow.modules.api.chat;

import java.time.Instant;

/** Safe polling view. It deliberately never contains an image payload or temporary image token. */
public record RecognitionJobView(
    String jobId,
    String replySessionId,
    RecognitionJobStatus status,
    String errorCode,
    ChatResponse response,
    PendingReplyTaskView pendingTask,
    Instant createdAt,
    Instant updatedAt
) {
}
