package com.privateflow.modules.communication;

import java.time.LocalDateTime;

public record CommunicationMessageCorrection(
    long id,
    long messageId,
    String previousText,
    String correctedText,
    String correctedBy,
    LocalDateTime correctedAt) {
}
